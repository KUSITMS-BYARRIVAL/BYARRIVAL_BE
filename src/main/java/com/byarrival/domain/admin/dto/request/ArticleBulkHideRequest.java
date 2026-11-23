package com.byarrival.domain.admin.dto.request;

import com.byarrival.domain.admin.entity.ArticleHideReason;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * @param articleIds 숨길 기사 ID. 한 번에 최대 100개
 * @param reason     숨김 사유
 * @param memo       최대 100자. 사유가 ETC면 필수
 */
public record ArticleBulkHideRequest(
    @NotEmpty @Size(max = 100) List<@NotNull Long> articleIds,
    @NotNull ArticleHideReason reason,
    @Size(max = 100) String memo
) {

    @JsonIgnore
    @AssertTrue(message = "기타 사유는 메모가 필요합니다.")
    public boolean isMemoPresentForEtc() {
        return reason != ArticleHideReason.ETC || (memo != null && !memo.isBlank());
    }
}
