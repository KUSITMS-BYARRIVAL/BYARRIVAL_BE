package com.byarrival.domain.article.service;

import com.byarrival.common.monitoring.BatchAlertNotifier;
import com.byarrival.domain.ai.config.AnthropicProperties;
import com.byarrival.domain.ai.service.ClaudeClient.TokenUsage;
import com.byarrival.domain.article.entity.Article;
import com.byarrival.domain.article.service.ArticleStoryGrouper.GroupResult;
import com.byarrival.domain.article.service.ArticleStoryGrouper.StoryGroupingException;
import com.byarrival.domain.article.service.ArticleStoryGrouper.StoryInput;
import com.byarrival.domain.category.service.CategoryMappingService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 요약이 끝난 서비스일 기사에서 같은 사건 기사를 AI로 묶고, 묶음마다 먼저 고른 기사 1건만 추천 후보로 남긴다.
 * 실패해도 배치를 멈추지 않는다. 이 단계는 수집 때의 제목 기준 같은 사건 제외를 보완할 뿐이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleStoryService {

    private final ArticleStoryStore store;
    private final ArticleStoryGrouper grouper;
    private final CategoryMappingService categoryMappingService;
    private final AnthropicProperties anthropicProperties;
    private final BatchAlertNotifier batchAlertNotifier;

    public StoryResult dedupe(LocalDate serviceDate) {
        List<Article> articles = store.loadRecommendable(serviceDate);
        if (articles.size() < 2) {
            return StoryResult.skipped();
        }
        Map<Long, String> categoryNames = categoryMappingService.getActiveCategoryNames();
        List<StoryInput> inputs = articles.stream()
            .map(article -> new StoryInput(article.getId(),
                categoryNames.getOrDefault(article.getCategoryId(), ""), article.getTitle(),
                article.getSummaryOneLine()))
            .toList();

        GroupResult grouped;
        try {
            grouped = grouper.group(inputs);
        } catch (StoryGroupingException e) {
            log.warn("[같은 사건] serviceDate={} 묶기 실패, 건너뜀 - {}", serviceDate, e.getMessage());
            StoryResult result = StoryResult.failed(e.calls(), e.usage());
            notifyResult(serviceDate, result, Map.of());
            return result;
        } catch (RuntimeException e) {
            log.error("[같은 사건] serviceDate={} 묶기 중 오류, 건너뜀 - {}", serviceDate,
                e.getClass().getSimpleName(), e);
            StoryResult result = StoryResult.failed(0, new TokenUsage(0, 0));
            notifyResult(serviceDate, result, Map.of());
            return result;
        }

        Map<Long, Article> byId = articles.stream().collect(Collectors.toMap(Article::getId, Function.identity()));
        Map<Long, Long> duplicates = chooseDuplicates(grouped.groups(), byId);
        int marked = duplicates.isEmpty() ? 0 : store.markDuplicates(duplicates);
        StoryResult result = new StoryResult(true, grouped.groups().size(), marked, grouped.calls(),
            grouped.usage());
        log.info("[같은 사건] serviceDate={} 묶음 {}개, 추천 제외 {}건", serviceDate, result.groupCount(), marked);
        notifyResult(serviceDate, result, describe(grouped.groups(), duplicates, byId));
        return result;
    }

    /**
     * 묶음마다 먼저 고른 기사(ID가 가장 작은 기사)를 남기고 나머지를 뺀다. 기사는 카테고리 처리 순서와 인기순으로 저장되므로
     * ID 순서가 곧 고른 순서다. 빼면 그 카테고리의 추천 후보가 0건이 되는 기사는 남긴다.
     *
     * @return 뺄 기사 ID → 남길 기사 ID
     */
    static Map<Long, Long> chooseDuplicates(List<Set<Long>> groups, Map<Long, Article> articles) {
        Map<Long, Long> remaining = new HashMap<>();
        articles.values().forEach(article -> remaining.merge(article.getCategoryId(), 1L, Long::sum));
        Map<Long, Long> duplicates = new LinkedHashMap<>();
        for (Set<Long> group : groups) {
            List<Long> ids = group.stream().sorted().toList();
            Long representative = ids.getFirst();
            for (Long id : ids.subList(1, ids.size())) {
                Long categoryId = articles.get(id).getCategoryId();
                if (remaining.get(categoryId) <= 1) {
                    continue;
                }
                remaining.merge(categoryId, -1L, Long::sum);
                duplicates.put(id, representative);
            }
        }
        return duplicates;
    }

    // 알림에 보일 묶음별 "남김 ← 뺌" 목록
    private Map<Long, List<Long>> describe(List<Set<Long>> groups, Map<Long, Long> duplicates,
        Map<Long, Article> articles) {
        Map<Long, List<Long>> removedByKept = new LinkedHashMap<>();
        groups.stream()
            .map(group -> group.stream().min(Comparator.naturalOrder()).orElseThrow())
            .sorted()
            .forEach(kept -> removedByKept.put(kept, new ArrayList<>()));
        duplicates.forEach((removed, kept) -> removedByKept.get(kept).add(removed));
        removedByKept.values().removeIf(List::isEmpty);
        return removedByKept;
    }

    // 알림이 실패해도 정리 결과에는 영향을 주지 않는다
    private void notifyResult(LocalDate serviceDate, StoryResult result, Map<Long, List<Long>> removedByKept) {
        try {
            StringBuilder content = new StringBuilder()
                .append(result.succeeded() ? "🧩 **같은 사건 정리 완료**" : "⚠️ **같은 사건 정리 실패 (건너뜀)**")
                .append(" 서비스일 ").append(serviceDate).append('\n');
            if (result.succeeded()) {
                content.append("묶음 ").append(result.groupCount()).append("개 · 추천 제외 ")
                    .append(result.removedCount()).append("건\n");
            }
            content.append("호출 ").append(result.calls()).append("회 · 토큰 ")
                .append(String.format("입력 %,d / 출력 %,d", result.usage().inputTokens(),
                    result.usage().outputTokens()))
                .append(" · 예상 비용 $").append(String.format("%.4f",
                    anthropicProperties.estimateCost(result.usage().inputTokens(), result.usage().outputTokens())));
            removedByKept.forEach((kept, removed) ->
                content.append("\n남김 ").append(kept).append(" ← 제외 ").append(removed));
            batchAlertNotifier.send(content.toString());
        } catch (RuntimeException e) {
            log.warn("[같은 사건] 알림 실패 - {}", e.getMessage());
        }
    }

    /**
     * @param succeeded    AI 묶기에 성공했는지. 실패하면 아무 기사도 빼지 않는다
     * @param groupCount   같은 사건 묶음 수
     * @param removedCount 추천에서 뺀 기사 수
     */
    public record StoryResult(boolean succeeded, int groupCount, int removedCount, long calls, TokenUsage usage) {

        static StoryResult skipped() {
            return new StoryResult(true, 0, 0, 0, new TokenUsage(0, 0));
        }

        static StoryResult failed(long calls, TokenUsage usage) {
            return new StoryResult(false, 0, 0, calls, usage);
        }
    }
}
