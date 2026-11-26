package com.byarrival.domain.admin.controller;

import com.byarrival.common.response.ApiResponse;
import com.byarrival.domain.admin.dto.request.EditorPickCreateRequest;
import com.byarrival.domain.admin.dto.request.EditorPickOrderRequest;
import com.byarrival.domain.admin.dto.request.EditorPickUpdateRequest;
import com.byarrival.domain.admin.dto.response.AdminEditorPickResponses.EditorPickList;
import com.byarrival.domain.admin.dto.response.AdminEditorPickResponses.Item;
import com.byarrival.domain.admin.dto.response.AdminEditorPickResponses.LinkableArticle;
import com.byarrival.domain.admin.security.AdminPrincipal;
import com.byarrival.domain.admin.service.AdminEditorPickService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Editor Pick", description = "어드민 에디터 픽(스몰토크) 관리")
@Validated
@RestController
@RequestMapping("/api/admin/editor-picks")
@RequiredArgsConstructor
public class AdminEditorPickController {

    private final AdminEditorPickService editorPickService;

    @Operation(summary = "에디터 픽 목록", description = "노출일(기본 내일)의 에디터 픽을 앱과 같은 순서로 돌려준다. "
        + "숨긴 것은 넣고 삭제한 것은 뺀다. 상태: EXPOSED(노출 중)·EDITED(수정함)·HIDDEN(숨김).")
    @GetMapping
    public ApiResponse<EditorPickList> getEditorPicks(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate serviceDate) {
        return ApiResponse.ok(editorPickService.getEditorPicks(serviceDate));
    }

    @Operation(summary = "연결할 수 있는 기사", description = "오늘 노출 중이거나 노출일(기본 내일) 당일에 노출할 기사 중 "
        + "아직 에디터 픽에 쓰지 않은 것. 제목으로 검색하고 최대 100건을 돌려준다.")
    @GetMapping("/articles")
    public ApiResponse<List<LinkableArticle>> getLinkableArticles(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate serviceDate,
        @RequestParam(required = false) @Size(max = 50) String keyword) {
        return ApiResponse.ok(editorPickService.getLinkableArticles(serviceDate, keyword));
    }

    @Operation(summary = "에디터 픽 직접 추가", description = "노출일(기본 내일), 연결 기사, 건네는 한마디(40자). "
        + "지난 노출일은 409, 연결할 수 없는 기사는 400, 이미 에디터 픽이 있는 기사는 409.")
    @PostMapping
    public ApiResponse<Item> create(@AuthenticationPrincipal AdminPrincipal admin,
        @Valid @RequestBody EditorPickCreateRequest request) {
        return ApiResponse.ok(editorPickService.create(admin.id(), request.serviceDate(), request.articleId(),
            request.hook()));
    }

    @Operation(summary = "에디터 픽 수정", description = "편집 폼의 값을 모두 보낸다. 저장하면 상태가 수정함이 된다. "
        + "노출일을 옮기면 옮긴 날의 순서 뒤에 붙는다. 16시 알림을 이미 보냈으면 다시 보내지 않는다.")
    @PutMapping("/{editorPickId}")
    public ApiResponse<Item> update(@AuthenticationPrincipal AdminPrincipal admin, @PathVariable Long editorPickId,
        @Valid @RequestBody EditorPickUpdateRequest request) {
        return ApiResponse.ok(editorPickService.update(admin.id(), editorPickId, request.serviceDate(),
            request.articleId(), request.hook()));
    }

    @Operation(summary = "에디터 픽 숨김", description = "앱에서 바로 사라진다. 이미 숨겼어도 성공이다.")
    @PostMapping("/{editorPickId}/hide")
    public ApiResponse<Item> hide(@PathVariable Long editorPickId) {
        return ApiResponse.ok(editorPickService.hide(editorPickId));
    }

    @Operation(summary = "에디터 픽 숨김 해제", description = "연결 기사가 숨겨져 있으면 여전히 숨김으로 보인다.")
    @PostMapping("/{editorPickId}/restore")
    public ApiResponse<Item> restore(@PathVariable Long editorPickId) {
        return ApiResponse.ok(editorPickService.restore(editorPickId));
    }

    @Operation(summary = "에디터 픽 삭제", description = "목록과 앱에서 바로 사라진다. 삭제한 자리는 배치가 다시 채우지 않는다.")
    @DeleteMapping("/{editorPickId}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal AdminPrincipal admin, @PathVariable Long editorPickId) {
        editorPickService.delete(admin.id(), editorPickId);
        return ApiResponse.ok();
    }

    @Operation(summary = "에디터 픽 순서 변경", description = "그 노출일의 에디터 픽(숨긴 것 포함) ID를 빠짐없이 원하는 순서대로 "
        + "보낸다. 빠지거나 다른 날 것이 섞이면 400. 홈 미리보기와 16시 알림(1번)이 이 순서를 따른다.")
    @PutMapping("/order")
    public ApiResponse<EditorPickList> reorder(@Valid @RequestBody EditorPickOrderRequest request) {
        return ApiResponse.ok(editorPickService.reorder(request.serviceDate(), request.editorPickIds()));
    }
}
