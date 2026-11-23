package com.byarrival.domain.admin.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * @param articleIds 복구할 기사 ID. 한 번에 최대 100개
 */
public record ArticleBulkRestoreRequest(
    @NotEmpty @Size(max = 100) List<@NotNull Long> articleIds
) {
}
