package com.byarrival.domain.admin.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * 에디터 픽 수정(EDITOR-05). 편집 폼의 값을 모두 보낸다.
 *
 * @param serviceDate 노출일. 오늘이나 그 뒤
 * @param articleId   연결 기사. 오늘 노출 중이거나 노출일 당일 기사만 고를 수 있다
 * @param hook        건네는 한마디. 최대 40자
 */
public record EditorPickUpdateRequest(
    @NotNull LocalDate serviceDate,
    @NotNull Long articleId,
    @NotBlank @Size(max = 40) String hook
) {
}
