package com.byarrival.domain.feed.repository;

import com.byarrival.domain.feed.entity.RssFeed;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RssFeedRepository extends JpaRepository<RssFeed, Long> {

    boolean existsByFeedUrl(String feedUrl);

    List<RssFeed> findAllByActiveTrue();
}
