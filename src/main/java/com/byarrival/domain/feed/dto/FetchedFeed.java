package com.byarrival.domain.feed.dto;

import com.byarrival.domain.feed.entity.RssFeed;
import java.util.List;

/**
 * Feed 한 건을 호출해 파싱한 결과. 기사 저장에 필요한 Feed 정보를 함께 담는다.
 *
 * @param rssCategory Feed에 등록된 언론사 카테고리. Item에 category가 없을 때 매핑에 쓴다.
 */
public record FetchedFeed(Long feedId, String pressName, String rssCategory, List<RssItem> items) {

    public FetchedFeed {
        items = List.copyOf(items);
    }

    public static FetchedFeed of(RssFeed feed, List<RssItem> items) {
        return new FetchedFeed(feed.getId(), feed.getPressName(), feed.getRssCategory(), items);
    }
}
