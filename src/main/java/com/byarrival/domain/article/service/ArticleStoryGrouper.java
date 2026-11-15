package com.byarrival.domain.article.service;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.domain.ai.exception.AiErrorCode;
import com.byarrival.domain.ai.service.ClaudeClient;
import com.byarrival.domain.ai.service.ClaudeClient.ClaudeRequest;
import com.byarrival.domain.ai.service.ClaudeClient.ClaudeResult;
import com.byarrival.domain.ai.service.ClaudeClient.TokenUsage;
import com.byarrival.domain.article.config.ArticleStoryProperties;
import com.byarrival.domain.article.dto.ArticleStoryOutput;
import com.byarrival.domain.article.dto.ArticleStoryOutput.StoryGroup;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * 같은 서비스일 기사 전체를 AI로 한 번에 보내 같은 사건 기사끼리 묶는다. 제목만으로는 못 잡는 표현이 다른 같은 사건을 잡는다.
 */
@Slf4j
@Component
public class ArticleStoryGrouper {

    static final String SYSTEM_PROMPT_PATH = "prompts/article-story-system.txt";

    private final ClaudeClient claudeClient;
    private final ArticleStoryProperties properties;
    private final String systemPrompt;

    public ArticleStoryGrouper(ClaudeClient claudeClient, ArticleStoryProperties properties) {
        this.claudeClient = claudeClient;
        this.properties = properties;
        this.systemPrompt = loadSystemPrompt();
    }

    /**
     * 검증에 실패하면 최대 maxRetries회 다시 요청한다. 끝내 실패하면 예외를 던진다.
     *
     * @return 같은 사건 묶음(2건 이상)과 쓴 토큰
     */
    public GroupResult group(List<StoryInput> articles) {
        ClaudeRequest request = new ClaudeRequest(properties.model(), properties.maxTokens(),
            properties.outputEffort(), systemPrompt, userMessage(articles));
        long calls = 0;
        long inputTokens = 0;
        long outputTokens = 0;
        BusinessException lastError = null;
        for (int attempt = 1; attempt <= properties.maxRetries() + 1; attempt++) {
            calls++;
            try {
                ClaudeResult<ArticleStoryOutput> result = claudeClient.generate(request, ArticleStoryOutput.class);
                inputTokens += result.usage().inputTokens();
                outputTokens += result.usage().outputTokens();
                List<Set<Long>> groups = validate(result.output(), articles);
                return new GroupResult(groups, calls, new TokenUsage(inputTokens, outputTokens));
            } catch (BusinessException e) {
                lastError = e;
                log.warn("[AI 같은 사건] {}번째 시도 실패 - {}", attempt, e.getMessage());
            }
        }
        throw new StoryGroupingException(lastError, calls, new TokenUsage(inputTokens, outputTokens));
    }

    /**
     * 없는 기사 번호나 두 묶음에 든 기사가 있으면 응답 전체를 실패로 본다. 한 묶음 안의 중복 번호는 하나로 치고,
     * 1건뿐인 묶음은 묶음이 아니므로 버린다.
     */
    static List<Set<Long>> validate(ArticleStoryOutput output, List<StoryInput> articles) {
        if (output == null || output.groups() == null) {
            throw invalid("응답이 비어 있음");
        }
        Set<Long> expected = new HashSet<>();
        articles.forEach(article -> expected.add(article.articleId()));
        Set<Long> grouped = new HashSet<>();
        List<Set<Long>> groups = new ArrayList<>();
        for (StoryGroup group : output.groups()) {
            if (group == null || group.articleIds() == null) {
                throw invalid("묶음의 articleIds가 비어 있음");
            }
            Set<Long> ids = new LinkedHashSet<>(group.articleIds());
            if (ids.contains(null) || !expected.containsAll(ids)) {
                throw invalid("입력에 없는 articleId");
            }
            if (ids.stream().anyMatch(grouped::contains)) {
                throw invalid("두 묶음에 들어간 articleId");
            }
            if (ids.size() >= 2) {
                grouped.addAll(ids);
                groups.add(Set.copyOf(ids));
            }
        }
        return groups;
    }

    // 2026-11-11 운영 기사로 시험한 형식 그대로 보낸다: "번호 | 카테고리 | 제목 | 한 줄 요약"
    static String userMessage(List<StoryInput> articles) {
        List<String> lines = new ArrayList<>();
        lines.add("기사 목록 (번호 | 카테고리 | 제목 | 한 줄 요약)");
        for (StoryInput article : articles) {
            lines.add(article.articleId() + " | " + article.categoryName() + " | " + oneLine(article.title()) + " | "
                + oneLine(article.oneLine()));
        }
        return String.join("\n", lines);
    }

    // 줄 바꿈이 섞이면 한 줄에 기사 하나라는 형식이 깨진다
    private static String oneLine(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").strip();
    }

    private static BusinessException invalid(String reason) {
        return new BusinessException(AiErrorCode.AI_RESPONSE_INVALID, reason);
    }

    private static String loadSystemPrompt() {
        try {
            return new ClassPathResource(SYSTEM_PROMPT_PATH).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("같은 사건 프롬프트를 읽지 못했습니다: " + SYSTEM_PROMPT_PATH, e);
        }
    }

    public record StoryInput(Long articleId, String categoryName, String title, String oneLine) {
    }

    public record GroupResult(List<Set<Long>> groups, long calls, TokenUsage usage) {
    }

    /**
     * 다시 요청해도 묶지 못한 경우. 실패한 호출에도 비용이 들었으므로 쓴 토큰을 함께 담는다.
     */
    public static class StoryGroupingException extends RuntimeException {

        private final long calls;
        private final TokenUsage usage;

        public StoryGroupingException(BusinessException cause, long calls, TokenUsage usage) {
            super(cause == null ? "같은 사건 묶기 실패" : cause.getMessage(), cause);
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
