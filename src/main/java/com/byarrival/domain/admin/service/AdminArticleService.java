package com.byarrival.domain.admin.service;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.common.exception.CommonErrorCode;
import com.byarrival.domain.admin.dto.request.AdminArticleSearchRequest;
import com.byarrival.domain.admin.dto.response.AdminArticleResponses.BulkResult;
import com.byarrival.domain.admin.dto.response.AdminArticleResponses.Category;
import com.byarrival.domain.admin.dto.response.AdminArticleResponses.Detail;
import com.byarrival.domain.admin.dto.response.AdminArticleResponses.Filters;
import com.byarrival.domain.admin.dto.response.AdminArticleResponses.History;
import com.byarrival.domain.admin.dto.response.AdminArticleResponses.Item;
import com.byarrival.domain.admin.dto.response.AdminArticleResponses.Page;
import com.byarrival.domain.admin.dto.response.AdminArticleResponses.Quiz;
import com.byarrival.domain.admin.dto.response.AdminArticleResponses.Steps;
import com.byarrival.domain.admin.dto.response.AdminArticleResponses.TabCounts;
import com.byarrival.domain.admin.dto.response.AdminArticleResponses.VisibilityResult;
import com.byarrival.domain.admin.entity.AdminArticleState;
import com.byarrival.domain.admin.entity.ArticleHideReason;
import com.byarrival.domain.admin.entity.ArticleVisibilityHistory;
import com.byarrival.domain.admin.exception.AdminErrorCode;
import com.byarrival.domain.admin.repository.AdminArticleRepository;
import com.byarrival.domain.admin.repository.AdminArticleRepository.ArticleRow;
import com.byarrival.domain.admin.repository.AdminArticleRepository.QuizRow;
import com.byarrival.domain.admin.repository.ArticleVisibilityHistoryRepository;
import com.byarrival.domain.article.entity.Article;
import com.byarrival.domain.article.service.ArticleQueryService;
import com.byarrival.domain.category.service.CategoryMappingService;
import com.byarrival.domain.quiz.entity.QuizChoice;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * 어드민 기사 목록·상세·숨김·복구(ARTICLE-01·02·04·05·07).
 * 앱의 추천·노선·스몰토크는 visibility를 보고 숨긴 기사를 빼므로, 여기서는 상태와 이력만 바꾼다.
 */
@Service
@RequiredArgsConstructor
public class AdminArticleService {

    static final int PAGE_SIZE = 20;
    private static final String VISIBLE = "VISIBLE";
    private static final String HIDDEN = "HIDDEN";
    private static final TypeReference<List<QuizChoice>> CHOICES = new TypeReference<>() {
    };

    private final AdminArticleRepository repository;
    private final ArticleVisibilityHistoryRepository historyRepository;
    private final ArticleQueryService articleQueryService;
    private final CategoryMappingService categoryMappingService;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Page getArticles(AdminArticleSearchRequest search) {
        if (search.collectedFrom() != null && search.collectedTo() != null
            && search.collectedFrom().isAfter(search.collectedTo())) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
        }
        Map<AdminArticleState, Long> counts = repository.countByState(search);
        long all = counts.values().stream().mapToLong(Long::longValue).sum();
        long total = search.tab().state() == null ? all : counts.getOrDefault(search.tab().state(), 0L);
        int page = search.page();
        List<Item> items = repository.findPage(search, PAGE_SIZE, (page - 1L) * PAGE_SIZE).stream()
            .map(row -> Item.of(row, Steps.from(row)))
            .toList();
        int totalPages = (int) Math.ceil((double) total / PAGE_SIZE);
        return new Page(new TabCounts(all, counts.getOrDefault(AdminArticleState.EXPOSED, 0L),
            counts.getOrDefault(AdminArticleState.HIDDEN, 0L), counts.getOrDefault(AdminArticleState.ERROR, 0L)),
            items, page, PAGE_SIZE, total, totalPages, page < totalPages);
    }

    @Transactional(readOnly = true)
    public Filters getFilters() {
        List<Category> categories = categoryMappingService.getActiveCategoryNames().entrySet().stream()
            .map(entry -> new Category(entry.getKey(), entry.getValue()))
            .toList();
        return new Filters(repository.findPressNames(), categories);
    }

    @Transactional(readOnly = true)
    public Detail getArticle(Long articleId) {
        ArticleRow row = repository.findRow(articleId)
            .orElseThrow(() -> new BusinessException(AdminErrorCode.ADMIN_ARTICLE_NOT_FOUND));
        Article article = articleQueryService.findAllByIds(List.of(articleId)).get(articleId);
        List<String> summary = Stream.of(article.getSummarySentence1(), article.getSummarySentence2(),
            article.getSummarySentence3()).filter(Objects::nonNull).toList();
        Integer readingMinutes = article.getReadingMinutes() == null ? null
            : article.getReadingMinutes().setScale(0, RoundingMode.CEILING).intValue();
        Quiz quiz = repository.findActiveQuiz(articleId).map(this::toQuiz).orElse(null);
        List<History> histories = repository.findHistory(articleId).stream().map(History::from).toList();
        return new Detail(row.id(), row.state(), row.title(), row.pressName(), row.categoryId(), row.categoryName(),
            row.difficulty(), article.getOriginalUrl(), article.getSourceUrl(), article.getPublishedAt(),
            row.collectedAt(), article.getServiceDate(), article.getSummaryOneLine(), summary,
            article.getSummaryKeywords() == null ? List.of() : article.getSummaryKeywords(), readingMinutes,
            row.viewCount(), Steps.from(row), quiz, histories);
    }

    /**
     * 이미 숨긴 기사면 이력을 남기지 않고 그대로 둔다.
     */
    @Transactional
    public VisibilityResult hide(Long adminId, Long articleId, ArticleHideReason reason, String memo) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Long> changed = repository.changeVisibility(List.of(articleId), VISIBLE, HIDDEN, now);
        if (changed.isEmpty()) {
            requireChangeable(articleId);
        }
        saveHideHistories(adminId, changed, reason, memo, now);
        return result(articleId);
    }

    /**
     * 숨긴 기사만 복구한다. 이미 노출 중이면 그대로 두고, 원문이 삭제된 기사는 복구하지 않는다.
     */
    @Transactional
    public VisibilityResult restore(Long adminId, Long articleId) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Long> changed = repository.changeVisibility(List.of(articleId), HIDDEN, VISIBLE, now);
        if (changed.isEmpty()) {
            requireChangeable(articleId);
        }
        saveRestoreHistories(adminId, changed, now);
        return result(articleId);
    }

    @Transactional
    public BulkResult hideAll(Long adminId, List<Long> articleIds, ArticleHideReason reason, String memo) {
        LocalDateTime now = LocalDateTime.now(clock);
        Set<Long> requested = new LinkedHashSet<>(articleIds);
        List<Long> changed = repository.changeVisibility(requested, VISIBLE, HIDDEN, now);
        saveHideHistories(adminId, changed, reason, memo, now);
        return bulkResult(requested, changed);
    }

    @Transactional
    public BulkResult restoreAll(Long adminId, List<Long> articleIds) {
        LocalDateTime now = LocalDateTime.now(clock);
        Set<Long> requested = new LinkedHashSet<>(articleIds);
        List<Long> changed = repository.changeVisibility(requested, HIDDEN, VISIBLE, now);
        saveRestoreHistories(adminId, changed, now);
        return bulkResult(requested, changed);
    }

    private void requireChangeable(Long articleId) {
        String visibility = repository.findVisibility(articleId)
            .orElseThrow(() -> new BusinessException(AdminErrorCode.ADMIN_ARTICLE_NOT_FOUND));
        if (!VISIBLE.equals(visibility) && !HIDDEN.equals(visibility)) {
            throw new BusinessException(AdminErrorCode.ADMIN_ARTICLE_UNAVAILABLE);
        }
    }

    private void saveHideHistories(Long adminId, List<Long> articleIds, ArticleHideReason reason, String memo,
        LocalDateTime now) {
        String normalizedMemo = memo == null || memo.isBlank() ? null : memo.strip();
        historyRepository.saveAll(articleIds.stream()
            .map(id -> ArticleVisibilityHistory.hide(id, adminId, reason, normalizedMemo, now))
            .toList());
    }

    private void saveRestoreHistories(Long adminId, List<Long> articleIds, LocalDateTime now) {
        historyRepository.saveAll(articleIds.stream()
            .map(id -> ArticleVisibilityHistory.restore(id, adminId, now))
            .toList());
    }

    private VisibilityResult result(Long articleId) {
        ArticleRow row = repository.findRow(articleId)
            .orElseThrow(() -> new BusinessException(AdminErrorCode.ADMIN_ARTICLE_NOT_FOUND));
        return new VisibilityResult(row.id(), row.state());
    }

    private static BulkResult bulkResult(Set<Long> requested, List<Long> changed) {
        Set<Long> changedSet = Set.copyOf(changed);
        return new BulkResult(requested.stream().filter(changedSet::contains).toList(),
            requested.stream().filter(id -> !changedSet.contains(id)).toList());
    }

    private Quiz toQuiz(QuizRow row) {
        return new Quiz(row.question(), jsonMapper.readValue(row.choicesJson(), CHOICES), row.answerSeq(),
            row.explanation());
    }
}
