package com.byarrival.domain.feed.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.byarrival.support.TestcontainersConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(TestcontainersConfig.class)
class FeedUrlUniqueConstraintTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void 중복_판별에_쓰는_제약_이름이_실제_스키마와_같다() {
        List<String> uniqueConstraints = jdbcTemplate.queryForList("""
            SELECT conname FROM pg_constraint
            WHERE conrelid = 'rss_feed'::regclass AND contype = 'u'
            """, String.class);

        assertThat(uniqueConstraints).containsExactly(FeedService.FEED_URL_UNIQUE_CONSTRAINT);
    }
}
