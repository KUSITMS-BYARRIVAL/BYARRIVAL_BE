package com.byarrival.domain.admin.service;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.domain.admin.dto.response.AdminArticleResponses.Detail;
import com.byarrival.domain.admin.dto.response.AdminArticleResponses.RegenerateResult;
import com.byarrival.domain.admin.entity.AdminArticleState;
import com.byarrival.domain.admin.entity.RegenerateStep;
import com.byarrival.domain.admin.exception.AdminErrorCode;
import com.byarrival.domain.admin.repository.AdminArticleRepository;
import com.byarrival.domain.admin.repository.AdminArticleRepository.ArticleRow;
import com.byarrival.domain.article.entity.Article;
import com.byarrival.domain.article.service.ArticleQueryService;
import com.byarrival.domain.article.service.ArticleRegenerationService;
import com.byarrival.domain.article.service.ArticleRegenerationService.SummaryFailure;
import com.byarrival.domain.article.service.ArticleRegenerationService.SummaryOutcome;
import com.byarrival.domain.quiz.service.QuizGenerationService;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 어드민 기사 재생성(ARTICLE-06).
 * <ul>
 *     <li>숨긴 기사와 오류 기사만 재생성한다. 노출 중·중복·배치 처리 중인 기사와 원문이 삭제된 기사는 거절한다.</li>
 *     <li>요약 재생성은 요약을 덮어쓴 뒤 퀴즈도 이어서 다시 만든다. 퀴즈는 요약을 보고 만들기 때문이다.</li>
 *     <li>퀴즈 재생성은 요약은 두고 퀴즈만 새 버전으로 만든다. 이전 버전은 지우지 않는다.</li>
 *     <li>숨긴 기사는 숨김 그대로 두고, 오류 기사는 성공하면 바로 노출 대상이 된다.</li>
 *     <li>AI 호출이 길어 트랜잭션으로 묶지 않는다. 단계마다 검증을 통과한 결과만 저장하고 바로 응답한다.</li>
 *     <li>횟수 제한은 없다.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class AdminArticleRegenerateService {

    private static final Set<AdminArticleState> REGENERATABLE = Set.of(AdminArticleState.HIDDEN,
        AdminArticleState.ERROR);
    private static final String QUIZ_FAILED = "AI가 만든 퀴즈가 검증을 통과하지 못했습니다. 다시 시도해 주세요.";

    private final AdminArticleRepository repository;
    private final AdminArticleService adminArticleService;
    private final ArticleQueryService articleQueryService;
    private final ArticleRegenerationService articleRegenerationService;
    private final QuizGenerationService quizGenerationService;

    public RegenerateResult regenerateSummary(Long articleId) {
        requireRegeneratable(articleId);
        SummaryOutcome summary = articleRegenerationService.regenerateSummary(articleId);
        if (!summary.success()) {
            return result(articleId, RegenerateStep.FAILED, RegenerateStep.SKIPPED, reasonOf(summary.failure()));
        }
        boolean quiz = quizGenerationService.regenerate(article(articleId));
        return result(articleId, RegenerateStep.SUCCESS, quiz ? RegenerateStep.SUCCESS : RegenerateStep.FAILED,
            quiz ? null : QUIZ_FAILED);
    }

    public RegenerateResult regenerateQuiz(Long articleId) {
        requireRegeneratable(articleId);
        Article article = article(articleId);
        if (article.getSummarySentence1() == null) {
            throw new BusinessException(AdminErrorCode.ADMIN_ARTICLE_NO_SUMMARY);
        }
        boolean quiz = quizGenerationService.regenerate(article);
        return result(articleId, RegenerateStep.SKIPPED, quiz ? RegenerateStep.SUCCESS : RegenerateStep.FAILED,
            quiz ? null : QUIZ_FAILED);
    }

    private void requireRegeneratable(Long articleId) {
        ArticleRow row = repository.findRow(articleId)
            .orElseThrow(() -> new BusinessException(AdminErrorCode.ADMIN_ARTICLE_NOT_FOUND));
        String visibility = repository.findVisibility(articleId).orElse(null);
        if (!"VISIBLE".equals(visibility) && !"HIDDEN".equals(visibility)) {
            throw new BusinessException(AdminErrorCode.ADMIN_ARTICLE_UNAVAILABLE);
        }
        if (!REGENERATABLE.contains(row.state())) {
            throw new BusinessException(AdminErrorCode.ADMIN_ARTICLE_NOT_REGENERATABLE);
        }
    }

    private Article article(Long articleId) {
        return articleQueryService.findAllByIds(List.of(articleId)).get(articleId);
    }

    private RegenerateResult result(Long articleId, RegenerateStep summary, RegenerateStep quiz,
        String failureReason) {
        Detail detail = adminArticleService.getArticle(articleId);
        return new RegenerateResult(articleId, summary, quiz, failureReason, detail);
    }

    private static String reasonOf(SummaryFailure failure) {
        return switch (failure) {
            case CRAWL_FAILED -> "원문을 다시 가져오지 못했습니다. 원문 주소를 확인해 주세요.";
            case AI_FAILED -> "AI가 만든 요약이 검증을 통과하지 못했습니다. 다시 시도해 주세요.";
            case NO_CATEGORY -> "카테고리가 없는 기사라 요약할 수 없습니다.";
            case NOT_FOUND -> "기사를 찾을 수 없습니다.";
        };
    }
}
