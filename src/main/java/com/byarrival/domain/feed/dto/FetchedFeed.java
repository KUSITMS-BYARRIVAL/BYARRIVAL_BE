package com.byarrival.domain.feed.dto;

/**
 * Feed 한 건을 호출해 받은 RSS 원문(XML). 파싱은 다음 단계에서 한다.
 */
public record FetchedFeed(Long feedId, byte[] content) {

    public static FetchedFeed of(Long feedId, byte[] content) {
        return new FetchedFeed(feedId, content);
    }
}
