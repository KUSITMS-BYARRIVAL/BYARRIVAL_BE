package com.byarrival.domain.user.dto.request;

import com.byarrival.domain.user.entity.JobGroup;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * @param jobGroups 바꾼 뒤의 직군. 지금은 1개만 보낸다
 */
public record JobGroupsRequest(
    @NotNull @Size(min = 1, max = JobGroup.MAX_SELECTION) List<@NotNull JobGroup> jobGroups
) {
}
