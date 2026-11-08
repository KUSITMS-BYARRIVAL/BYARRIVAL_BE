package com.byarrival.domain.notification.dto.response;

public record TestPushResponse(
    int sentDeviceCount
) {

    public static TestPushResponse from(int sentDeviceCount) {
        return new TestPushResponse(sentDeviceCount);
    }
}
