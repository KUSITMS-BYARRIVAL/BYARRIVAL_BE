package com.byarrival.domain.admin.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * 에디터 픽 직접 추가(EDITOR-03).
 *
 * @param serviceDate 노출일. 보내지 않으면 내일
 * @param articleId   연결 기사. 오늘 노출 중이거나 노출일 당일 기사만 고를 수 있다
 * @param hook        건네는 한마디. 최대 40자
 */
public record EditorPickCreateRequest(
    LocalDate serviceDate,
    @NotNull Long articleId,
    @NotBlank @Size(max = 40) String hook
) {
}
