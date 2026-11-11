package com.byarrival.domain.user.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * @param categoryIds 관심 분야. 이 목록으로 통째로 바꾸므로 1개 이상이어야 한다.
 */
public record InterestCategoriesRequest(
    @NotNull @Size(min = 1) List<@NotNull Long> categoryIds
) {
}
