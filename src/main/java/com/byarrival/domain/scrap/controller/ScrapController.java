package com.byarrival.domain.scrap.controller;

import com.byarrival.common.response.ApiResponse;
import com.byarrival.common.security.LoginUserId;
import com.byarrival.domain.scrap.dto.response.LibraryResponse;
import com.byarrival.domain.scrap.dto.response.LibrarySort;
import com.byarrival.domain.scrap.dto.response.LibraryType;
import com.byarrival.domain.scrap.dto.response.ScrapStatusResponse;
import com.byarrival.domain.scrap.service.ScrapService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Scrap", description = "보관함·기사 저장")
@RestController
@RequestMapping("/api/v1/scraps")
@RequiredArgsConstructor
public class ScrapController {

    private final ScrapService scrapService;

    @Operation(summary = "보관함 조회",
        description = "탭별 저장 개수와 목록(type=ARTICLE|SMALLTALK). 기사 탭은 저장 시각 최신순(LATEST)·오래된순(OLDEST) 정렬과 카테고리 필터를 쓴다. "
            + "categories는 저장 기사가 있는 카테고리만 담는다(0건 카테고리 숨김). 숨겼거나 원문이 삭제된 기사는 available=false.")
    @GetMapping
    public ApiResponse<LibraryResponse> getLibrary(
        @LoginUserId Long userId,
        @RequestParam(defaultValue = "ARTICLE") LibraryType type,
        @RequestParam(defaultValue = "LATEST") LibrarySort sort,
        @RequestParam(required = false) Long categoryId) {
        return ApiResponse.ok(scrapService.getLibrary(userId, type, sort, categoryId));
    }

    @Operation(summary = "기사 저장", description = "이미 저장한 기사면 그대로 둔다. 숨겼거나 삭제된 기사는 SCRAP_TARGET_NOT_FOUND.")
    @PutMapping("/articles/{articleId}")
    public ApiResponse<ScrapStatusResponse> saveArticle(@LoginUserId Long userId, @PathVariable Long articleId) {
        return ApiResponse.ok(scrapService.saveArticle(userId, articleId));
    }

    @Operation(summary = "기사 저장 해제", description = "기사 화면의 저장 토글. 저장하지 않은 기사면 그대로 둔다.")
    @DeleteMapping("/articles/{articleId}")
    public ApiResponse<ScrapStatusResponse> unsaveArticle(@LoginUserId Long userId, @PathVariable Long articleId) {
        return ApiResponse.ok(scrapService.unsaveArticle(userId, articleId));
    }

    @Operation(summary = "스몰토크 저장", description = "이미 저장한 스몰토크면 그대로 둔다. 삭제된 스몰토크는 SCRAP_TARGET_NOT_FOUND.")
    @PutMapping("/smalltalks/{smalltalkId}")
    public ApiResponse<ScrapStatusResponse> saveSmalltalk(@LoginUserId Long userId, @PathVariable Long smalltalkId) {
        return ApiResponse.ok(scrapService.saveSmalltalk(userId, smalltalkId));
    }

    @Operation(summary = "스몰토크 저장 해제", description = "저장하지 않은 스몰토크면 그대로 둔다.")
    @DeleteMapping("/smalltalks/{smalltalkId}")
    public ApiResponse<ScrapStatusResponse> unsaveSmalltalk(@LoginUserId Long userId, @PathVariable Long smalltalkId) {
        return ApiResponse.ok(scrapService.unsaveSmalltalk(userId, smalltalkId));
    }

    @Operation(summary = "보관함에서 삭제", description = "토스트의 [되돌리기]는 restore로 원래 자리에 되살린다.")
    @DeleteMapping("/{scrapId}")
    public ApiResponse<Void> delete(@LoginUserId Long userId, @PathVariable Long scrapId) {
        scrapService.delete(userId, scrapId);
        return ApiResponse.ok();
    }

    @Operation(summary = "삭제 되돌리기", description = "원래 저장 시각을 유지해 목록의 같은 자리로 돌아온다.")
    @PostMapping("/{scrapId}/restore")
    public ApiResponse<Void> restore(@LoginUserId Long userId, @PathVariable Long scrapId) {
        scrapService.restore(userId, scrapId);
        return ApiResponse.ok();
    }
}
