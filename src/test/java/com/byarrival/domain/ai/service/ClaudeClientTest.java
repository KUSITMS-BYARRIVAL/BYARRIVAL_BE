package com.byarrival.domain.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.core.ObjectMappers;
import com.anthropic.core.RequestOptions;
import com.anthropic.core.http.Headers;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.services.blocking.MessageService;
import com.byarrival.common.exception.BusinessException;
import com.byarrival.domain.ai.exception.AiErrorCode;
import com.byarrival.domain.ai.exception.AiRateLimitedException;
import com.byarrival.domain.ai.exception.AiResponseInvalidException;
import com.byarrival.domain.ai.service.ClaudeClient.ClaudeRequest;
import com.byarrival.domain.ai.service.ClaudeClient.ClaudeResult;
import com.byarrival.domain.ai.service.ClaudeClient.TokenUsage;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;

class ClaudeClientTest {

    private static final ClaudeRequest REQUEST = new ClaudeRequest(
        "claude-haiku-5-5", 4000, OutputConfig.Effort.LOW, "시스템 지시", "<title>제목</title>");

    private final AtomicReference<MessageCreateParams> sentParams = new AtomicReference<>();
    private MessageService messageService;
    private ClaudeClient claudeClient;
    private SimpleMeterRegistry meterRegistry;

    record Sample(String title, List<String> summary) {
    }

    @BeforeEach
    void setUp() {
        AnthropicClient anthropicClient = mock(AnthropicClient.class);
        // 구조화 출력 create는 SDK 기본 메서드라 실제 코드를 타게 하고, HTTP 요청만 가짜로 바꾼다
        messageService = mock(MessageService.class, withSettings().defaultAnswer(Answers.CALLS_REAL_METHODS));
        given(anthropicClient.messages()).willReturn(messageService);
        meterRegistry = new SimpleMeterRegistry();
        claudeClient = new ClaudeClient(anthropicClient, meterRegistry);
    }

    @Test
    void 응답_JSON을_지정한_타입으로_바꾸고_요청에_모델_지시문_effort_스키마를_담는다() {
        respond("end_turn", "{\"title\":\"제목\",\"summary\":[\"하나\",\"둘\",\"셋\"]}");

        ClaudeResult<Sample> result = claudeClient.generate(REQUEST, Sample.class);

        assertThat(result.output()).isEqualTo(new Sample("제목", List.of("하나", "둘", "셋")));
        assertThat(result.usage()).isEqualTo(new TokenUsage(10, 10));
        MessageCreateParams params = sentParams.get();
        assertThat(params.model().asString()).isEqualTo("claude-haiku-5-5");
        assertThat(params.maxTokens()).isEqualTo(4000);
        TextBlockParam system = params.system().orElseThrow().asTextBlockParams().getFirst();
        assertThat(system.text()).isEqualTo("시스템 지시");
        // 배치에서 되풀이해 보내는 지시문은 캐시한다
        assertThat(system.cacheControl()).isPresent();
        OutputConfig outputConfig = params.outputConfig().orElseThrow();
        assertThat(outputConfig.effort()).contains(OutputConfig.Effort.LOW);
        assertThat(outputConfig.format()).isPresent();
    }

    @Test
    void 호출마다_입력_출력_토큰_수를_기록한다() {
        respond("end_turn", "{\"title\":\"제목\",\"summary\":[]}");

        claudeClient.generate(REQUEST, Sample.class);
        claudeClient.generate(REQUEST, Sample.class);

        assertThat(meterRegistry.counter("ai.tokens", "model", "claude-haiku-5-5", "type", "input").count())
            .isEqualTo(20);
        assertThat(meterRegistry.counter("ai.tokens", "model", "claude-haiku-5-5", "type", "output").count())
            .isEqualTo(20);
    }

    @Test
    void 캐시에_쓰고_읽은_토큰을_입력_토큰과_나눠_센다() {
        respond("end_turn", "{\"title\":\"제목\",\"summary\":[]}", 20, 300);

        ClaudeResult<Sample> result = claudeClient.generate(REQUEST, Sample.class);

        assertThat(result.usage()).isEqualTo(new TokenUsage(10, 10, 20, 300));
        assertThat(meterRegistry.counter("ai.tokens", "model", "claude-haiku-5-5", "type", "cache_write").count())
            .isEqualTo(20);
        assertThat(meterRegistry.counter("ai.tokens", "model", "claude-haiku-5-5", "type", "cache_read").count())
            .isEqualTo(300);
        assertThat(result.usage().describe()).isEqualTo("입력 330 (캐시 읽기 300 · 쓰기 20) / 출력 10");
    }

    @Test
    void 응답이_max_tokens로_끝나면_응답_오류로_본다() {
        respond("max_tokens", "{\"title\":\"제목\"");

        assertError(AiErrorCode.AI_RESPONSE_INVALID);
    }

    @Test
    void 응답은_받았지만_쓸_수_없으면_쓴_토큰을_예외에_담는다() {
        respond("max_tokens", "{\"title\":\"제목\"");

        assertThatThrownBy(() -> claudeClient.generate(REQUEST, Sample.class))
            .isInstanceOf(AiResponseInvalidException.class)
            .extracting(e -> ((AiResponseInvalidException) e).getUsage())
            .isEqualTo(new TokenUsage(10, 10));
    }

    @Test
    void 정상_종료했지만_JSON이_출력_타입과_맞지_않으면_응답_오류로_본다() {
        respond("end_turn", "{\"title\":\"제목\",\"summary\":{\"first\":\"하나\"}}");

        assertError(AiErrorCode.AI_RESPONSE_INVALID);
    }

    @Test
    void 정상_종료했지만_JSON이_깨져_있으면_응답_오류로_본다() {
        respond("end_turn", "{\"title\":\"제목\",\"summ");

        assertError(AiErrorCode.AI_RESPONSE_INVALID);
    }

    @Test
    void 응답을_거절하면_응답_오류로_본다() {
        respond("refusal", "");

        assertError(AiErrorCode.AI_RESPONSE_INVALID);
    }

    @Test
    void 연결에_실패하면_호출_오류로_본다() {
        given(messageService.create(any(MessageCreateParams.class), any(RequestOptions.class)))
            .willThrow(new AnthropicIoException("connection reset"));

        assertError(AiErrorCode.AI_CALL_FAILED);
    }

    @Test
    void SDK_재시도_후에도_요청_한도를_넘으면_한도_초과로_구분한다() {
        given(messageService.create(any(MessageCreateParams.class), any(RequestOptions.class)))
            .willThrow(RateLimitException.builder()
                .headers(Headers.builder().build())
                .body(JsonValue.from(Map.of()))
                .build());

        assertThatThrownBy(() -> claudeClient.generate(REQUEST, Sample.class))
            .isInstanceOf(AiRateLimitedException.class)
            .hasMessage("RateLimitException status=429")
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(AiErrorCode.AI_CALL_FAILED);
    }

    private void assertError(AiErrorCode errorCode) {
        assertThatThrownBy(() -> claudeClient.generate(REQUEST, Sample.class))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(errorCode);
    }

    private void respond(String stopReason, String text) {
        respond(stopReason, text, 0, 0);
    }

    private void respond(String stopReason, String text, long cacheWriteTokens, long cacheReadTokens) {
        Message message = message(stopReason, text, cacheWriteTokens, cacheReadTokens);
        given(messageService.create(any(MessageCreateParams.class), any(RequestOptions.class)))
            .willAnswer(invocation -> {
                sentParams.set(invocation.getArgument(0));
                return message;
            });
    }

    private static Message message(String stopReason, String text, long cacheWriteTokens, long cacheReadTokens) {
        String content = text.isEmpty() ? "[]"
            : "[{\"type\":\"text\",\"text\":" + quote(text) + ",\"citations\":null}]";
        String json = """
            {"id":"msg_test","type":"message","role":"assistant","model":"claude-haiku-5-5",
             "content":%s,"stop_reason":"%s","stop_sequence":null,"stop_details":null,"container":null,
             "usage":{"input_tokens":10,"output_tokens":10,"cache_creation_input_tokens":%d,
                      "cache_read_input_tokens":%d,"cache_creation":null,"server_tool_use":null,
                      "service_tier":"standard","inference_geo":null}}
            """.formatted(content, stopReason, cacheWriteTokens, cacheReadTokens);
        try {
            return ObjectMappers.jsonMapper().readValue(json, Message.class);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String quote(String text) {
        try {
            return ObjectMappers.jsonMapper().writeValueAsString(text);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
