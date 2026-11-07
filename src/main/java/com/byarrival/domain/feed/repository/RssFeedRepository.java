package com.byarrival.domain.feed.repository;

import com.byarrival.domain.feed.entity.RssFeed;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RssFeedRepository extends JpaRepository<RssFeed, Long> {

    boolean existsByFeedUrl(String feedUrl);

    List<RssFeed> findAllByActiveTrue();

    @Modifying(clearAutomatically = true)
    @Query("UPDATE RssFeed f SET f.lastFetchedAt = :fetchedAt, f.updatedAt = :fetchedAt,"
        + " f.consecutiveFailureCount = 0, f.lastFailedServiceDate = null WHERE f.id IN :feedIds")
    int updateLastFetchedAt(@Param("feedIds") Collection<Long> feedIds, @Param("fetchedAt") LocalDateTime fetchedAt);
}
