package com.byarrival.domain.category.controller;

import com.byarrival.common.response.ApiResponse;
import com.byarrival.domain.category.dto.response.CategoryResponse;
import com.byarrival.domain.category.service.CategoryMappingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Category", description = "관심 분야")
@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryMappingService categoryMappingService;

    @Operation(summary = "관심 분야 목록 조회", description = "온보딩·설정에서 고를 수 있는 활성 카테고리를 화면 순서대로 돌려준다.")
    @GetMapping
    public ApiResponse<List<CategoryResponse>> getCategories() {
        List<CategoryResponse> categories = categoryMappingService.getActiveCategoryNames().entrySet().stream()
            .map(entry -> CategoryResponse.of(entry.getKey(), entry.getValue()))
            .toList();
        return ApiResponse.ok(categories);
    }
}
