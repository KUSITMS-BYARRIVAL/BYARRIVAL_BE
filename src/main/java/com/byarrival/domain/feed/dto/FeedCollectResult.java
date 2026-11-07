package com.byarrival.domain.feed.dto;

import java.util.List;

/**
 * 활성 Feed 수집 결과. 호출이나 파싱에 실패한 Feed는 failedFeedIds에 담는다.
 */
public record FeedCollectResult(List<FetchedFeed> fetchedFeeds, List<Long> failedFeedIds) {

    public FeedCollectResult {
        fetchedFeeds = List.copyOf(fetchedFeeds);
        failedFeedIds = List.copyOf(failedFeedIds);
    }

    public static FeedCollectResult of(List<FetchedFeed> fetchedFeeds, List<Long> failedFeedIds) {
        return new FeedCollectResult(fetchedFeeds, failedFeedIds);
    }

    // 수집할 Feed가 있었는데 하나도 수집하지 못했다
    public boolean allFailed() {
        return fetchedFeeds.isEmpty() && !failedFeedIds.isEmpty();
    }
}
