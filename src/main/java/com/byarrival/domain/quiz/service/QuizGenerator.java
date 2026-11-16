package com.byarrival.domain.quiz.service;

import com.byarrival.domain.ai.service.ClaudeClient;
import com.byarrival.domain.ai.service.ClaudeClient.ClaudeRequest;
import com.byarrival.domain.ai.service.ClaudeClient.ClaudeResult;
import com.byarrival.domain.quiz.config.QuizProperties;
import com.byarrival.domain.quiz.dto.QuizOutput;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * 기사 요약으로 3지선다 퀴즈 1문제를 만든다(AI-03).
 */
@Component
public class QuizGenerator {

    static final String SYSTEM_PROMPT_PATH = "prompts/quiz-system.txt";

    private final ClaudeClient claudeClient;
    private final QuizProperties properties;
    private final String systemPrompt;

    public QuizGenerator(ClaudeClient claudeClient, QuizProperties properties) {
        this.claudeClient = claudeClient;
        this.properties = properties;
        this.systemPrompt = loadSystemPrompt();
    }

    public ClaudeResult<QuizOutput> generate(QuizSource source) {
        return claudeClient.generate(request(userMessage(source)), QuizOutput.class);
    }

    /**
     * 검증에 실패한 이전 응답과 어긴 규칙을 함께 보내 다시 쓰게 한다.
     */
    public ClaudeResult<QuizOutput> regenerate(QuizSource source, QuizOutput previous, String problems) {
        String message = userMessage(source) + "\n\n<previous_answer>\n"
            + "question: " + previous.question() + "\n"
            + "answer: " + previous.answer() + "\n"
            + "wrongAnswers: " + previous.wrongAnswers() + "\n"
            + "explanation: " + previous.explanation() + "\n"
            + "</previous_answer>\n"
            + "<problems>\n" + problems + "\n</problems>\n"
            + "이전 답이 위 규칙을 어겼습니다. 문제를 모두 고쳐 처음부터 다시 쓰세요.";
        return claudeClient.generate(request(message), QuizOutput.class);
    }

    private ClaudeRequest request(String message) {
        return new ClaudeRequest(properties.model(), properties.maxTokens(), properties.outputEffort(), systemPrompt,
            message);
    }

    static String userMessage(QuizSource source) {
        return "<title>" + source.title() + "</title>\n"
            + "<category>" + source.categoryName() + "</category>\n"
            + "<one_line>" + source.oneLine() + "</one_line>\n"
            + "<summary>\n" + String.join("\n", source.summary()) + "\n</summary>";
    }

    private static String loadSystemPrompt() {
        try {
            return new ClassPathResource(SYSTEM_PROMPT_PATH).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("퀴즈 프롬프트를 읽지 못했습니다: " + SYSTEM_PROMPT_PATH, e);
        }
    }

    /**
     * 퀴즈를 만들 기사. 사용자가 앱에서 읽은 내용(제목, 한 줄 요약, 3줄 요약)만 넘긴다.
     */
    public record QuizSource(Long articleId, String title, String categoryName, String oneLine, List<String> summary) {
    }
}
