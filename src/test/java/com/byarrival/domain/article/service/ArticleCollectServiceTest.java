package com.byarrival.domain.article.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.byarrival.domain.article.entity.Article;
import com.byarrival.domain.article.entity.ArticleProcessStatus;
import com.byarrival.domain.article.repository.ArticleRepository;
import com.byarrival.domain.category.entity.CategoryMapping;
import com.byarrival.domain.category.repository.CategoryMappingRepository;
import com.byarrival.domain.feed.dto.FetchedFeed;
import com.byarrival.domain.feed.dto.RssItem;
import com.byarrival.domain.feed.entity.RssFeed;
import com.byarrival.domain.feed.repository.RssFeedRepository;
import com.byarrival.support.TestcontainersConfig;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(TestcontainersConfig.class)
class ArticleCollectServiceTest {

    private static final LocalDate SERVICE_DATE = LocalDate.of(2026, 11, 7);
    private static final LocalDateTime PUBLISHED_AT = LocalDateTime.of(2026, 11, 6, 10, 0);

    @Autowired
    private ArticleCollectService articleCollectService;

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private RssFeedRepository rssFeedRepository;

    @Autowired
    private CategoryMappingRepository categoryMappingRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long feedId;
    private Long economyId;

    @BeforeEach
    void setUp() {
        feedId = rssFeedRepository.save(RssFeed.create("한국경제", "https://news.example.com/rss.xml", "경제")).getId();
        economyId = insertCategory("경제", 1);
    }

    @AfterEach
    void tearDown() {
        articleRepository.deleteAll();
        categoryMappingRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM category");
        rssFeedRepository.deleteAll();
    }

    @Test
    void RSS_Item을_원본_기사로_저장한다() {
        RssItem item = item("guid-1", "https://news.example.com/1", "<p>" + "가".repeat(1000) + "</p>", List.of());

        int saved = articleCollectService.saveCollected(feed(item), SERVICE_DATE);

        Article article = articleRepository.findAll().getFirst();
        assertThat(saved).isEqualTo(1);
        assertThat(article.getFeedId()).isEqualTo(feedId);
        assertThat(article.getGuid()).isEqualTo("guid-1");
        assertThat(article.getOriginalUrl()).isEqualTo("https://news.example.com/1");
        assertThat(article.getTitle()).isEqualTo("제목 guid-1");
        assertThat(article.getPressName()).isEqualTo("한국경제");
        assertThat(article.getPublishedAt()).isEqualTo(PUBLISHED_AT);
        assertThat(article.getServiceDate()).isEqualTo(SERVICE_DATE);
        assertThat(article.getRawContent()).isEqualTo("<p>" + "가".repeat(1000) + "</p>");
        assertThat(article.getProcessStatus()).isEqualTo(ArticleProcessStatus.COLLECTED);
    }

    @Test
    void RSS_description은_본문으로_보지_않아_정제와_읽기시간을_계산하지_않는다() {
        articleCollectService.saveCollected(
            feed(item("guid-1", "https://news.example.com/1", "<p>" + "가".repeat(1000) + "</p>", List.of())),
            SERVICE_DATE);

        Article article = articleRepository.findAll().getFirst();
        assertThat(article.getCleanedContent()).isNull();
        assertThat(article.getReadingMinutes()).isNull();
    }

    @Test
    void Item에_source가_있으면_실제_언론사명으로_저장한다() {
        RssItem fromGoogle = new RssItem("guid-1", "https://news.google.com/rss/articles/1", "제목", null,
            PUBLISHED_AT, List.of(), "한겨레");
        RssItem tooLongSource = new RssItem("guid-2", "https://news.google.com/rss/articles/2", "제목", null,
            PUBLISHED_AT, List.of(), "가".repeat(51));

        articleCollectService.saveCollected(feed(fromGoogle, tooLongSource), SERVICE_DATE);

        assertThat(articleRepository.findAll()).extracting(Article::getGuid, Article::getPressName)
            .containsExactlyInAnyOrder(tuple("guid-1", "한겨레"), tuple("guid-2", "한국경제"));
    }

    @Test
    void Item의_category로_서비스_카테고리를_매핑한다() {
        Long societyId = insertCategory("사회", 2);
        categoryMappingRepository.save(CategoryMapping.create(feedId, "사회일반", societyId));
        RssItem item = item("guid-1", "https://news.example.com/1", "본문", List.of("기획", "사회일반"));

        articleCollectService.saveCollected(feed(item), SERVICE_DATE);

        Article article = articleRepository.findAll().getFirst();
        assertThat(article.getCategoryId()).isEqualTo(societyId);
        assertThat(article.getRssCategory()).isEqualTo("사회일반");
    }

    @Test
    void Item에_category가_없으면_Feed의_category로_매핑한다() {
        categoryMappingRepository.save(CategoryMapping.create(feedId, "경제", economyId));

        articleCollectService.saveCollected(feed(item("guid-1", "https://news.example.com/1", "본문", List.of())),
            SERVICE_DATE);

        Article article = articleRepository.findAll().getFirst();
        assertThat(article.getCategoryId()).isEqualTo(economyId);
        assertThat(article.getRssCategory()).isEqualTo("경제");
    }

    @Test
    void Item의_category가_매핑되지_않으면_Feed의_category로_매핑한다() {
        categoryMappingRepository.save(CategoryMapping.create(feedId, "경제", economyId));
        RssItem item = item("guid-1", "https://news.example.com/1", "본문", List.of("연예"));

        articleCollectService.saveCollected(feed(item), SERVICE_DATE);

        Article article = articleRepository.findAll().getFirst();
        assertThat(article.getCategoryId()).isEqualTo(economyId);
        assertThat(article.getRssCategory()).isEqualTo("경제");
    }

    @Test
    void 매핑되지_않는_기사는_카테고리_없이_저장한다() {
        RssItem item = item("guid-1", "https://news.example.com/1", "본문", List.of("연예"));

        articleCollectService.saveCollected(feed(item), SERVICE_DATE);

        Article article = articleRepository.findAll().getFirst();
        assertThat(article.getCategoryId()).isNull();
        assertThat(article.getRssCategory()).isEqualTo("연예");
    }

    @Test
    void 같은_Feed를_다시_저장해도_기사가_중복_저장되지_않는다() {
        FetchedFeed feed = feed(item("guid-1", "https://news.example.com/1", "본문", List.of()),
            item(null, "https://news.example.com/2", "본문", List.of()));
        articleCollectService.saveCollected(feed, SERVICE_DATE);

        int saved = articleCollectService.saveCollected(feed, SERVICE_DATE);

        assertThat(saved).isZero();
        assertThat(articleRepository.count()).isEqualTo(2);
    }

    @Test
    void GUID나_URL이_이미_있는_기사는_건너뛴다() {
        articleCollectService.saveCollected(
            feed(item("guid-1", "https://news.example.com/1", "본문", List.of())), SERVICE_DATE);

        int saved = articleCollectService.saveCollected(feed(
            item("guid-1", "https://news.example.com/changed-url", "본문", List.of()),
            item("guid-new", "https://news.example.com/1", "본문", List.of()),
            item("guid-2", "https://news.example.com/2", "본문", List.of()),
            item("guid-2", "https://news.example.com/2-dup", "본문", List.of())), SERVICE_DATE);

        assertThat(saved).isEqualTo(1);
        assertThat(articleRepository.findAll()).extracting(Article::getGuid)
            .containsExactlyInAnyOrder("guid-1", "guid-2");
    }

    @Test
    void 컬럼_길이를_넘는_기사는_저장하지_않는다() {
        RssItem longTitle = new RssItem("guid-1", "https://news.example.com/1", "가".repeat(301), "본문",
            PUBLISHED_AT, List.of(), null);
        RssItem longUrl = item("guid-2", "https://news.example.com/" + "a".repeat(1000), "본문", List.of());
        RssItem longGuid = item("g".repeat(501), "https://news.example.com/3", "본문", List.of());
        RssItem ok = item("guid-4", "https://news.example.com/4", "본문", List.of("가".repeat(51)));

        int saved = articleCollectService.saveCollected(feed(longTitle, longUrl, longGuid, ok), SERVICE_DATE);

        List<Article> articles = articleRepository.findAll();
        assertThat(saved).isEqualTo(1);
        assertThat(articles).extracting(Article::getGuid).containsExactly("guid-4");
        // 50자를 넘는 Item category는 버리고 Feed의 category를 남긴다
        assertThat(articles.getFirst().getRssCategory()).isEqualTo("경제");
    }

    private Long insertCategory(String name, int displayOrder) {
        return jdbcTemplate.queryForObject(
            "INSERT INTO category (name, display_order) VALUES (?, ?) RETURNING id", Long.class, name, displayOrder);
    }

    private FetchedFeed feed(RssItem... items) {
        return new FetchedFeed(feedId, "한국경제", "경제", List.of(items));
    }

    private RssItem item(String guid, String link, String content, List<String> categories) {
        return new RssItem(guid, link, "제목 " + guid, content, PUBLISHED_AT, categories, null);
    }
}
