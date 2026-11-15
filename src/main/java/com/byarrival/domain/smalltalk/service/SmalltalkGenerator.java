package com.byarrival.domain.smalltalk.service;

import com.byarrival.domain.ai.service.ClaudeClient;
import com.byarrival.domain.ai.service.ClaudeClient.ClaudeRequest;
import com.byarrival.domain.ai.service.ClaudeClient.ClaudeResult;
import com.byarrival.domain.smalltalk.config.SmalltalkProperties;
import com.byarrival.domain.smalltalk.dto.SmalltalkOutput;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * 기사 요약으로 대화를 여는 한 줄(hook)을 만든다(TALK-01). 기사 1건당 AI 호출 1회다.
 */
@Component
public class SmalltalkGenerator {

    static final String SYSTEM_PROMPT_PATH = "prompts/smalltalk-system.txt";

    private final ClaudeClient claudeClient;
    private final SmalltalkProperties properties;
    private final String systemPrompt;

    public SmalltalkGenerator(ClaudeClient claudeClient, SmalltalkProperties properties) {
        this.claudeClient = claudeClient;
        this.properties = properties;
        this.systemPrompt = loadSystemPrompt();
    }

    public ClaudeResult<SmalltalkOutput> generate(SmalltalkSource source) {
        return claudeClient.generate(request(userMessage(source)), SmalltalkOutput.class);
    }

    /**
     * 검증에 실패한 이전 응답과 어긴 규칙을 함께 보내 다시 쓰게 한다.
     */
    public ClaudeResult<SmalltalkOutput> regenerate(SmalltalkSource source, SmalltalkOutput previous, String problems) {
        String message = userMessage(source) + "\n\n<previous_answer>\nhook: " + previous.hook()
            + "\n</previous_answer>\n<problems>\n" + problems + "\n</problems>\n"
            + "이전 답이 위 규칙을 어겼습니다. 문제를 모두 고쳐 다시 쓰세요.";
        return claudeClient.generate(request(message), SmalltalkOutput.class);
    }

    private ClaudeRequest request(String message) {
        return new ClaudeRequest(properties.model(), properties.maxTokens(), properties.outputEffort(), systemPrompt,
            message);
    }

    static String userMessage(SmalltalkSource source) {
        return "<title>" + source.title() + "</title>\n"
            + "<category>" + source.categoryName() + "</category>\n"
            + "<one_line>" + source.oneLine() + "</one_line>\n"
            + "<summary>\n" + String.join("\n", source.summary()) + "\n</summary>";
    }

    private static String loadSystemPrompt() {
        try {
            return new ClassPathResource(SYSTEM_PROMPT_PATH).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("스몰토크 프롬프트를 읽지 못했습니다: " + SYSTEM_PROMPT_PATH, e);
        }
    }

    public record SmalltalkSource(Long articleId, Long categoryId, String title, String categoryName, String oneLine,
                                  List<String> summary) {
    }
}
