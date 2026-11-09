package com.byarrival.domain.notification.push;

import java.util.List;

/**
 * 푸시 발송 결과. invalidTokens는 앱 삭제 등으로 더 이상 쓸 수 없어 지워야 하는 토큰이다.
 */
public record PushResult(
    int successCount,
    List<String> invalidTokens
) {

    public static PushResult of(int successCount, List<String> invalidTokens) {
        return new PushResult(successCount, List.copyOf(invalidTokens));
    }
}
