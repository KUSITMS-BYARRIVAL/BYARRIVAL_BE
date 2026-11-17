package com.byarrival.domain.quiz.service;

import com.byarrival.domain.ai.service.ClaudeClient;
import com.byarrival.domain.ai.service.ClaudeClient.ClaudeRequest;
import com.byarrival.domain.ai.service.ClaudeClient.ClaudeResult;
import com.byarrival.domain.quiz.config.QuizCheckProperties;
import com.byarrival.domain.quiz.dto.QuizCheckOutput;
import com.byarrival.domain.quiz.service.QuizGenerator.QuizSource;
import com.byarrival.domain.quiz.service.QuizValidator.ValidatedQuiz;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * 만든 퀴즈를 출제와 별도의 호출로 요약만 보고 다시 풀어 본다(AI-04). 형식 검증으로는 알 수 없는
 * 틀린 정답, 정답이 될 수도 있는 오답, 문제와 엇나간 정답과 해설을 잡는다.
 */
@Component
public class QuizChecker {

    static final String SYSTEM_PROMPT_PATH = "prompts/quiz-check-system.txt";

    private final ClaudeClient claudeClient;
    private final QuizCheckProperties properties;
    private final String systemPrompt;

    public QuizChecker(ClaudeClient claudeClient, QuizCheckProperties properties) {
        this.claudeClient = claudeClient;
        this.properties = properties;
        this.systemPrompt = loadSystemPrompt();
    }

    public ClaudeResult<QuizCheckOutput> check(QuizSource source, ValidatedQuiz quiz) {
        ClaudeRequest request = new ClaudeRequest(properties.model(), properties.maxTokens(),
            properties.outputEffort(), systemPrompt, userMessage(source, quiz));
        return claudeClient.generate(request, QuizCheckOutput.class);
    }

    static String userMessage(QuizSource source, ValidatedQuiz quiz) {
        String choices = IntStream.range(0, quiz.choices().size())
            .mapToObj(i -> (i + 1) + ". " + quiz.choices().get(i))
            .collect(Collectors.joining("\n"));
        return "<title>" + source.title() + "</title>\n"
            + "<one_line>" + source.oneLine() + "</one_line>\n"
            + "<summary>\n" + String.join("\n", source.summary()) + "\n</summary>\n"
            + "<question>" + quiz.question() + "</question>\n"
            + "<choices>\n" + choices + "\n</choices>\n"
            + "<explanation>" + quiz.explanation() + "</explanation>";
    }

    private static String loadSystemPrompt() {
        try {
            return new ClassPathResource(SYSTEM_PROMPT_PATH).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("퀴즈 검수 프롬프트를 읽지 못했습니다: " + SYSTEM_PROMPT_PATH, e);
        }
    }
}
