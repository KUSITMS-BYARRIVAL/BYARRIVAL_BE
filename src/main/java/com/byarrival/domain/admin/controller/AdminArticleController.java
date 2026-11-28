package com.byarrival.domain.admin.controller;

import com.byarrival.common.response.ApiResponse;
import com.byarrival.domain.admin.dto.request.AdminArticleSearchRequest;
import com.byarrival.domain.admin.dto.request.ArticleBulkHideRequest;
import com.byarrival.domain.admin.dto.request.ArticleBulkRestoreRequest;
import com.byarrival.domain.admin.dto.request.ArticleHideRequest;
import com.byarrival.domain.admin.dto.response.AdminArticleResponses.BulkResult;
import com.byarrival.domain.admin.dto.response.AdminArticleResponses.Detail;
import com.byarrival.domain.admin.dto.response.AdminArticleResponses.Filters;
import com.byarrival.domain.admin.dto.response.AdminArticleResponses.Page;
import com.byarrival.domain.admin.dto.response.AdminArticleResponses.RegenerateResult;
import com.byarrival.domain.admin.dto.response.AdminArticleResponses.VisibilityResult;
import com.byarrival.domain.admin.security.AdminPrincipal;
import com.byarrival.domain.admin.service.AdminArticleRegenerateService;
import com.byarrival.domain.admin.service.AdminArticleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Article", description = "어드민 기사 관리")
@RestController
@RequestMapping("/api/admin/articles")
@RequiredArgsConstructor
public class AdminArticleController {

    private final AdminArticleService adminArticleService;
    private final AdminArticleRegenerateService regenerateService;

    @Operation(summary = "기사 목록", description = "탭별 건수와 기사 목록. 페이지당 20개이고 page는 1부터 시작한다.")
    @GetMapping
    public ApiResponse<Page> getArticles(@Valid @ModelAttribute AdminArticleSearchRequest request) {
        return ApiResponse.ok(adminArticleService.getArticles(request));
    }

    @Operation(summary = "기사 필터 선택지", description = "언론사와 카테고리 목록")
    @GetMapping("/filters")
    public ApiResponse<Filters> getFilters() {
        return ApiResponse.ok(adminArticleService.getFilters());
    }

    @Operation(summary = "기사 상세", description = "요약, 메타 정보, 퀴즈, 처리 상태, 숨김·복구 이력")
    @GetMapping("/{articleId}")
    public ApiResponse<Detail> getArticle(@PathVariable Long articleId) {
        return ApiResponse.ok(adminArticleService.getArticle(articleId));
    }

    @Operation(summary = "기사 숨김", description = "사유는 필수이고 ETC는 메모도 필수다. 이미 숨긴 기사면 그대로 둔다.")
    @PostMapping("/{articleId}/hide")
    public ApiResponse<VisibilityResult> hide(@AuthenticationPrincipal AdminPrincipal admin,
        @PathVariable Long articleId, @Valid @RequestBody ArticleHideRequest request) {
        return ApiResponse.ok(adminArticleService.hide(admin.id(), articleId, request.reason(), request.memo()));
    }

    @Operation(summary = "기사 복구", description = "숨긴 기사를 다시 노출 대상으로 바꾼다. 이미 노출 중이면 그대로 둔다.")
    @PostMapping("/{articleId}/restore")
    public ApiResponse<VisibilityResult> restore(@AuthenticationPrincipal AdminPrincipal admin,
        @PathVariable Long articleId) {
        return ApiResponse.ok(adminArticleService.restore(admin.id(), articleId));
    }

    @Operation(summary = "기사 요약 재생성", description = "숨긴 기사나 오류 기사만 된다. 3줄 요약·한 줄 요약·키워드를 다시 "
        + "만들어 덮어쓰고, 이어서 퀴즈도 새 버전으로 만든다. 난이도는 바꾸지 않는다. 숨긴 기사는 숨김 그대로이고, 오류 기사는 "
        + "성공하면 바로 노출된다. AI를 호출하므로 수십 초까지 걸릴 수 있고 결과를 바로 응답한다.")
    @PostMapping("/{articleId}/regenerate/summary")
    public ApiResponse<RegenerateResult> regenerateSummary(@PathVariable Long articleId) {
        return ApiResponse.ok(regenerateService.regenerateSummary(articleId));
    }

    @Operation(summary = "기사 퀴즈 재생성", description = "숨긴 기사나 오류 기사만 된다. 요약은 두고 퀴즈만 새 버전으로 만든다. "
        + "이전 버전은 지우지 않아 이미 퀴즈를 받은 노선은 받은 버전을 그대로 쓴다. 요약이 없으면 409.")
    @PostMapping("/{articleId}/regenerate/quiz")
    public ApiResponse<RegenerateResult> regenerateQuiz(@PathVariable Long articleId) {
        return ApiResponse.ok(regenerateService.regenerateQuiz(articleId));
    }

    @Operation(summary = "기사 일괄 숨김", description = "최대 100개. 숨길 수 없는 기사는 제외하고 결과로 알려 준다.")
    @PostMapping("/bulk-hide")
    public ApiResponse<BulkResult> hideAll(@AuthenticationPrincipal AdminPrincipal admin,
        @Valid @RequestBody ArticleBulkHideRequest request) {
        return ApiResponse.ok(adminArticleService.hideAll(admin.id(), request.articleIds(), request.reason(),
            request.memo()));
    }

    @Operation(summary = "기사 일괄 복구", description = "최대 100개. 복구할 수 없는 기사는 제외하고 결과로 알려 준다.")
    @PostMapping("/bulk-restore")
    public ApiResponse<BulkResult> restoreAll(@AuthenticationPrincipal AdminPrincipal admin,
        @Valid @RequestBody ArticleBulkRestoreRequest request) {
        return ApiResponse.ok(adminArticleService.restoreAll(admin.id(), request.articleIds()));
    }
}
