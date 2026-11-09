package com.byarrival.domain.notification.dto.response;

public record UnreadCountResponse(
    long count
) {

    public static UnreadCountResponse from(long count) {
        return new UnreadCountResponse(count);
    }
}
