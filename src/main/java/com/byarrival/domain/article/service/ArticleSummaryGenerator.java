package com.byarrival.domain.article.service;

import com.byarrival.domain.ai.service.ClaudeClient.ClaudeRequest;
import com.byarrival.domain.ai.service.ClaudeClient.ClaudeResult;
import com.byarrival.domain.ai.service.ClaudeClient;
import com.byarrival.domain.article.config.ArticleSummaryProperties;
import com.byarrival.domain.article.dto.ArticleSummaryOutput;
import com.byarrival.domain.article.entity.ArticleDifficulty;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * AI-06 프롬프트로 기사 1건의 3문장 요약을 생성한다(AI-01).
 */
@Component
public class ArticleSummaryGenerator {

    static final String SYSTEM_PROMPT_PATH = "prompts/article-summary-system.txt";

    private final ClaudeClient claudeClient;
    private final ArticleSummaryProperties properties;
    private final String systemPrompt;

    public ArticleSummaryGenerator(ClaudeClient claudeClient, ArticleSummaryProperties properties) {
        this.claudeClient = claudeClient;
        this.properties = properties;
        this.systemPrompt = loadSystemPrompt();
    }

    public ClaudeResult<ArticleSummaryOutput> generate(String title, String content, ArticleDifficulty difficulty) {
        ClaudeRequest request = new ClaudeRequest(properties.model(), properties.maxTokens(),
            properties.outputEffort(), systemPrompt, userMessage(title, content, difficulty));
        return claudeClient.generate(request, ArticleSummaryOutput.class);
    }

    /**
     * 검증에 실패한 이전 응답과 어긴 규칙을 함께 보내 다시 쓰게 한다.
     */
    public ClaudeResult<ArticleSummaryOutput> regenerate(String title, String content, ArticleDifficulty difficulty,
        ArticleSummaryOutput previous, String problems) {
        String message = userMessage(title, content, difficulty) + "\n\n" + feedback(previous, problems);
        ClaudeRequest request = new ClaudeRequest(properties.model(), properties.maxTokens(),
            properties.outputEffort(), systemPrompt, message);
        return claudeClient.generate(request, ArticleSummaryOutput.class);
    }

    static String feedback(ArticleSummaryOutput previous, String problems) {
        return "<previous_answer>\n"
            + "oneLine: " + previous.oneLine() + "\n"
            + "summary: " + previous.summary() + "\n"
            + "keywords: " + previous.keywords() + "\n"
            + "</previous_answer>\n"
            + "<problems>\n" + problems + "\n</problems>\n"
            + "이전 답이 위 규칙을 어겼습니다. 문제를 모두 고쳐 처음부터 다시 쓰세요. 규칙을 지킨 부분은 그대로 둬도 됩니다.";
    }

    static String userMessage(String title, String content, ArticleDifficulty difficulty) {
        return "<title>" + title + "</title>\n"
            + "<difficulty>" + difficulty.name() + "</difficulty>\n"
            + "<article>\n" + content + "\n</article>";
    }

    private static String loadSystemPrompt() {
        try {
            return new ClassPathResource(SYSTEM_PROMPT_PATH).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("요약 프롬프트를 읽지 못했습니다: " + SYSTEM_PROMPT_PATH, e);
        }
    }
}
