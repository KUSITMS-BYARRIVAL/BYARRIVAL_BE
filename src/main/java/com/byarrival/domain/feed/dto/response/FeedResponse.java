package com.byarrival.domain.feed.dto.response;

import com.byarrival.domain.feed.entity.RssFeed;

public record FeedResponse(
    Long feedId,
    String pressName,
    String feedUrl,
    String rssCategory,
    boolean active
) {

    public static FeedResponse from(RssFeed feed) {
        return new FeedResponse(
            feed.getId(),
            feed.getPressName(),
            feed.getFeedUrl(),
            feed.getRssCategory(),
            feed.isActive()
        );
    }
}
