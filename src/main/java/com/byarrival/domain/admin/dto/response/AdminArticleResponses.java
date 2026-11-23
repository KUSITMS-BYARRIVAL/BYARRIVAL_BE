package com.byarrival.domain.admin.dto.response;

import com.byarrival.domain.admin.entity.AdminArticleState;
import com.byarrival.domain.admin.entity.AdminStepStatus;
import com.byarrival.domain.admin.entity.ArticleHideReason;
import com.byarrival.domain.admin.entity.ArticleVisibilityAction;
import com.byarrival.domain.admin.repository.AdminArticleRepository.ArticleRow;
import com.byarrival.domain.admin.repository.AdminArticleRepository.HistoryRow;
import com.byarrival.domain.article.entity.ArticleDifficulty;
import com.byarrival.domain.article.entity.ArticleProcessStatus;
import com.byarrival.domain.quiz.entity.QuizChoice;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 어드민 기사 API 응답 모음.
 */
public final class AdminArticleResponses {

    private AdminArticleResponses() {
    }

    /**
     * @param counts        현재 검색 조건의 탭별 건수
     * @param page          현재 페이지(1부터)
     * @param totalElements 선택한 탭의 전체 건수
     */
    public record Page(TabCounts counts, List<Item> articles, int page, int size, long totalElements, int totalPages,
                       boolean hasNext) {
    }

    public record TabCounts(long all, long exposed, long hidden, long error) {
    }

    /**
     * @param viewCount 이 기사를 담은 사용자 수
     */
    public record Item(Long articleId, String title, String pressName, Long categoryId, String categoryName,
                       ArticleDifficulty difficulty, LocalDateTime collectedAt, Steps steps,
                       ArticleProcessStatus processStatus, AdminArticleState state, long viewCount) {

        public static Item of(ArticleRow row, Steps steps) {
            return new Item(row.id(), row.title(), row.pressName(), row.categoryId(), row.categoryName(),
                row.difficulty(), row.collectedAt(), steps, row.processStatus(), row.state(), row.viewCount());
        }
    }

    public record Steps(AdminStepStatus collect, AdminStepStatus summary, AdminStepStatus quiz) {

        public static Steps from(ArticleRow row) {
            return new Steps(AdminStepStatus.SUCCESS, summaryStep(row), quizStep(row));
        }

        private static AdminStepStatus summaryStep(ArticleRow row) {
            if (row.hasSummary()) {
                return AdminStepStatus.SUCCESS;
            }
            return switch (row.state()) {
                case PROCESSING -> AdminStepStatus.WAITING;
                case HIDDEN, DUPLICATE -> AdminStepStatus.SKIPPED;
                case ERROR, EXPOSED -> AdminStepStatus.FAILED;
            };
        }

        private static AdminStepStatus quizStep(ArticleRow row) {
            if (row.hasQuiz()) {
                return AdminStepStatus.SUCCESS;
            }
            if (!row.hasSummary() && row.state() == AdminArticleState.ERROR) {
                return AdminStepStatus.WAITING;
            }
            return switch (row.state()) {
                case PROCESSING -> AdminStepStatus.WAITING;
                case HIDDEN, DUPLICATE -> AdminStepStatus.SKIPPED;
                case ERROR, EXPOSED -> AdminStepStatus.FAILED;
            };
        }
    }

    public record Filters(List<String> pressNames, List<Category> categories) {
    }

    public record Category(Long categoryId, String name) {
    }

    /**
     * @param originalUrl    수집한 주소
     * @param sourceUrl      크롤링한 언론사 원문 주소. 없으면 null
     * @param readingMinutes 예상 읽기 시간(올림). 계산 전이면 null
     * @param quiz           활성 퀴즈. 없으면 null
     */
    public record Detail(Long articleId, AdminArticleState state, String title, String pressName, Long categoryId,
                         String categoryName, ArticleDifficulty difficulty, String originalUrl, String sourceUrl,
                         LocalDateTime publishedAt, LocalDateTime collectedAt, LocalDate serviceDate,
                         String summaryOneLine, List<String> summary, List<String> keywords, Integer readingMinutes,
                         long viewCount, Steps steps, Quiz quiz, List<History> histories) {
    }

    public record Quiz(String question, List<QuizChoice> choices, int answerSeq, String explanation) {
    }

    public record History(ArticleVisibilityAction action, ArticleHideReason reason, String memo, String adminEmail,
                          LocalDateTime createdAt) {

        public static History from(HistoryRow row) {
            return new History(row.action(), row.reason(), row.memo(), row.adminEmail(), row.createdAt());
        }
    }

    public record VisibilityResult(Long articleId, AdminArticleState state) {
    }

    /**
     * @param changedIds 실제로 바뀐 기사
     * @param skippedIds 없는 기사이거나 이미 그 상태라 제외한 기사
     */
    public record BulkResult(List<Long> changedIds, List<Long> skippedIds) {
    }
}
