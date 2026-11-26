package com.byarrival.domain.admin.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * 에디터 픽 순서 변경. 그 노출일의 에디터 픽(숨긴 것 포함, 삭제한 것 제외) ID를 빠짐없이 원하는 순서대로 보낸다.
 */
public record EditorPickOrderRequest(
    @NotNull LocalDate serviceDate,
    @NotEmpty @Size(max = 200) List<@NotNull Long> editorPickIds
) {
}
