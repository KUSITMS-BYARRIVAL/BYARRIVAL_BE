package com.byarrival.domain.article.service;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.domain.ai.exception.AiErrorCode;
import com.byarrival.domain.ai.service.ClaudeClient;
import com.byarrival.domain.ai.service.ClaudeClient.ClaudeRequest;
import com.byarrival.domain.ai.service.ClaudeClient.ClaudeResult;
import com.byarrival.domain.ai.service.ClaudeClient.TokenUsage;
import com.byarrival.domain.article.config.ArticleDifficultyProperties;
import com.byarrival.domain.article.dto.ArticleDifficultyOutput;
import com.byarrival.domain.article.dto.ArticleDifficultyOutput.Score;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * 같은 카테고리 기사들의 이해 난이도를 AI로 한 번에 1~10점으로 매긴다. 토큰을 아끼려고 본문은 앞부분만 보낸다.
 */
@Slf4j
@Component
public class ArticleDifficultyScorer {

    static final String SYSTEM_PROMPT_PATH = "prompts/article-difficulty-system.txt";
    static final int MIN_SCORE = 1;
    static final int MAX_SCORE = 10;

    private final ClaudeClient claudeClient;
    private final ArticleDifficultyProperties properties;
    private final String systemPrompt;

    public ArticleDifficultyScorer(ClaudeClient claudeClient, ArticleDifficultyProperties properties) {
        this.claudeClient = claudeClient;
        this.properties = properties;
        this.systemPrompt = loadSystemPrompt();
    }

    /**
     * 검증에 실패하면 최대 maxRetries회 다시 요청한다. 끝내 실패하면 예외를 던진다.
     *
     * @return 기사 ID별 점수와 쓴 토큰
     */
    public ScoreResult score(List<ScoreInput> articles) {
        ClaudeRequest request = new ClaudeRequest(properties.model(), properties.maxTokens(),
            properties.outputEffort(), systemPrompt, userMessage(articles, properties.leadLength()));
        long calls = 0;
        long inputTokens = 0;
        long outputTokens = 0;
        BusinessException lastError = null;
        for (int attempt = 1; attempt <= properties.maxRetries() + 1; attempt++) {
            calls++;
            try {
                ClaudeResult<ArticleDifficultyOutput> result = claudeClient.generate(request,
                    ArticleDifficultyOutput.class);
                inputTokens += result.usage().inputTokens();
                outputTokens += result.usage().outputTokens();
                Map<Long, Integer> scores = validate(result.output(), articles);
                return new ScoreResult(scores, calls, new TokenUsage(inputTokens, outputTokens));
            } catch (BusinessException e) {
                lastError = e;
                log.warn("[AI 난이도] {}번째 시도 실패 - {}", attempt, e.getMessage());
            }
        }
        throw new DifficultyScoringException(lastError, calls, new TokenUsage(inputTokens, outputTokens));
    }

    static Map<Long, Integer> validate(ArticleDifficultyOutput output, List<ScoreInput> articles) {
        if (output == null || output.scores() == null) {
            throw invalid("응답이 비어 있음");
        }
        Set<Long> expected = new HashSet<>();
        articles.forEach(article -> expected.add(article.articleId()));
        Map<Long, Integer> scores = new HashMap<>();
        for (Score score : output.scores()) {
            if (score == null || score.articleId() == null || !expected.contains(score.articleId())) {
                throw invalid("입력에 없는 articleId");
            }
            if (score.score() == null || score.score() < MIN_SCORE || score.score() > MAX_SCORE) {
                throw invalid("점수가 " + MIN_SCORE + "~" + MAX_SCORE + " 밖임");
            }
            if (scores.put(score.articleId(), score.score()) != null) {
                throw invalid("articleId 중복");
            }
        }
        if (!scores.keySet().equals(expected)) {
            throw invalid("점수가 빠진 기사 있음");
        }
        return scores;
    }

    static String userMessage(List<ScoreInput> articles, int leadLength) {
        List<String> parts = new ArrayList<>();
        for (ScoreInput article : articles) {
            parts.add("<article id=\"" + article.articleId() + "\">\n"
                + "<title>" + article.title() + "</title>\n"
                + "<lead>" + lead(article.content(), leadLength) + "</lead>\n"
                + "</article>");
        }
        return String.join("\n", parts);
    }

    private static String lead(String content, int leadLength) {
        String stripped = content.strip();
        if (stripped.codePointCount(0, stripped.length()) <= leadLength) {
            return stripped;
        }
        return stripped.substring(0, stripped.offsetByCodePoints(0, leadLength));
    }

    private static BusinessException invalid(String reason) {
        return new BusinessException(AiErrorCode.AI_RESPONSE_INVALID, reason);
    }

    private static String loadSystemPrompt() {
        try {
            return new ClassPathResource(SYSTEM_PROMPT_PATH).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("난이도 프롬프트를 읽지 못했습니다: " + SYSTEM_PROMPT_PATH, e);
        }
    }

    public record ScoreInput(Long articleId, String title, String content) {
    }

    public record ScoreResult(Map<Long, Integer> scores, long calls, TokenUsage usage) {
    }

    /**
     * 다시 요청해도 판정하지 못한 경우. 실패한 호출에도 비용이 들었으므로 쓴 토큰을 함께 담는다.
     */
    public static class DifficultyScoringException extends RuntimeException {

        private final long calls;
        private final TokenUsage usage;

        public DifficultyScoringException(BusinessException cause, long calls, TokenUsage usage) {
            super(cause == null ? "난이도 판정 실패" : cause.getMessage(), cause);
            this.calls = calls;
            this.usage = usage;
        }

        public long calls() {
            return calls;
        }

        public TokenUsage usage() {
            return usage;
        }
    }
}
