package com.byarrival.domain.region.controller;

import com.byarrival.common.response.ApiResponse;
import com.byarrival.domain.region.dto.response.RegionSidoResponse;
import com.byarrival.domain.region.service.RegionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Region", description = "근무 지역")
@RestController
@RequestMapping("/api/v1/regions")
@RequiredArgsConstructor
public class RegionController {

    private final RegionService regionService;

    @Operation(summary = "근무 지역 목록 조회",
        description = "온보딩·마이의 근무 지역 화면에 쓰는 시/도 17개와 각 시/군/구 목록을 한 번에 돌려준다. "
            + "시/도는 화면 순서, 시/군/구는 이름순이다. 검색은 이 목록에서 앱이 거른다. 저장할 때는 sigunguId를 보낸다.")
    @GetMapping
    public ApiResponse<List<RegionSidoResponse>> getRegions() {
        return ApiResponse.ok(regionService.getRegions());
    }
}
