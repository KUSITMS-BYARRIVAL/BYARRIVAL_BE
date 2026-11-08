package com.byarrival.domain.article.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.byarrival.domain.article.entity.Article;
import com.byarrival.domain.article.entity.ArticleContent;
import com.byarrival.domain.article.entity.ArticleProcessStatus;
import com.byarrival.domain.article.repository.ArticleContentRepository;
import com.byarrival.domain.article.repository.ArticleRepository;
import com.byarrival.domain.article.service.ArticleCollectService.CategoryFill;
import com.byarrival.domain.article.service.ArticleCrawler.CrawlResult;
import com.byarrival.domain.article.service.ArticleCrawler.CrawledArticle;
import com.byarrival.domain.category.entity.CategoryMapping;
import com.byarrival.domain.category.repository.CategoryMappingRepository;
import com.byarrival.domain.feed.dto.FetchedFeed;
import com.byarrival.domain.feed.dto.RssItem;
import com.byarrival.domain.feed.entity.RssFeed;
import com.byarrival.domain.feed.repository.RssFeedRepository;
import com.byarrival.support.TestcontainersConfig;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@Import(TestcontainersConfig.class)
class ArticleCollectServiceTest {

    private static final LocalDate SERVICE_DATE = LocalDate.of(2026, 11, 7);
    private static final LocalDateTime PUBLISHED_AT = LocalDateTime.of(2026, 11, 6, 10, 0);
    private static final String BODY = "가".repeat(1000);
    private static final int MAX_PER_CATEGORY = 20;
    private static final String GOOGLE = "https://news.google.com/rss/articles/";

    @Autowired
    private ArticleCollectService articleCollectService;

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private ArticleContentRepository articleContentRepository;

    @Autowired
    private RssFeedRepository rssFeedRepository;

    @Autowired
    private CategoryMappingRepository categoryMappingRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private ArticleCrawler articleCrawler;

    private Long feedId;
    private Long economyId;

    @BeforeEach
    void setUp() {
        feedId = rssFeedRepository.save(RssFeed.create("한국경제", "https://news.example.com/rss.xml", "경제")).getId();
        economyId = insertCategory("경제", 1);
        // 링크마다 다른 원문 주소를 돌려준다
        given(articleCrawler.crawl(anyString())).willAnswer(invocation -> CrawlResult.success(new CrawledArticle(
            "https://press.example.com/" + Integer.toHexString(invocation.getArgument(0, String.class).hashCode()),
            BODY)));
    }

    @AfterEach
    void tearDown() {
        articleContentRepository.deleteAll();
        articleRepository.deleteAll();
        categoryMappingRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM category");
        rssFeedRepository.deleteAll();
    }

    @Test
    void RSS_Item을_원본_기사로_저장한다() {
        RssItem item = item("guid-1", "https://news.example.com/1", "<p>" + "가".repeat(1000) + "</p>", List.of());

        int saved = articleCollectService.collect(feed(item), SERVICE_DATE);

        Article article = articleRepository.findAll().getFirst();
        assertThat(saved).isEqualTo(1);
        assertThat(article.getFeedId()).isEqualTo(feedId);
        assertThat(article.getGuid()).isEqualTo("guid-1");
        assertThat(article.getOriginalUrl()).isEqualTo("https://news.example.com/1");
        assertThat(article.getTitle()).isEqualTo(title("guid-1"));
        assertThat(article.getPressName()).isEqualTo("한국경제");
        assertThat(article.getPublishedAt()).isEqualTo(PUBLISHED_AT);
        assertThat(article.getServiceDate()).isEqualTo(SERVICE_DATE);
        assertThat(article.getRawContent()).isEqualTo("<p>" + "가".repeat(1000) + "</p>");
        assertThat(article.getProcessStatus()).isEqualTo(ArticleProcessStatus.COLLECTED);
    }

    @Test
    void RSS_description은_본문으로_보지_않아_본문과_읽기시간을_저장하지_않는다() {
        articleCollectService.collect(
            feed(item("guid-1", "https://news.example.com/1", "<p>" + "가".repeat(1000) + "</p>", List.of())),
            SERVICE_DATE);

        assertThat(articleContentRepository.count()).isZero();
        assertThat(articleRepository.findAll().getFirst().getReadingMinutes()).isNull();
    }

    @Test
    void Item에_source가_있으면_실제_언론사명으로_저장한다() {
        RssItem fromGoogle = new RssItem("guid-1", GOOGLE + "1", "첫째 제목", null, PUBLISHED_AT, List.of(), "한겨레");
        RssItem tooLongSource = new RssItem("guid-2", GOOGLE + "2", "둘째 제목", null, PUBLISHED_AT, List.of(),
            "가".repeat(51));

        articleCollectService.collect(feed(fromGoogle, tooLongSource), SERVICE_DATE);

        assertThat(articleRepository.findAll()).extracting(Article::getGuid, Article::getPressName)
            .containsExactlyInAnyOrder(tuple("guid-1", "한겨레"), tuple("guid-2", "한국경제"));
    }

    @Test
    void Item의_category로_서비스_카테고리를_매핑한다() {
        Long societyId = insertCategory("사회", 2);
        categoryMappingRepository.save(CategoryMapping.create(feedId, "사회일반", societyId));
        RssItem item = item("guid-1", "https://news.example.com/1", "본문", List.of("기획", "사회일반"));

        articleCollectService.collect(feed(item), SERVICE_DATE);

        Article article = articleRepository.findAll().getFirst();
        assertThat(article.getCategoryId()).isEqualTo(societyId);
        assertThat(article.getRssCategory()).isEqualTo("사회일반");
    }

    @Test
    void Item에_category가_없으면_Feed의_category로_매핑한다() {
        mapEconomy();

        articleCollectService.collect(feed(item("guid-1", "https://news.example.com/1", "본문", List.of())),
            SERVICE_DATE);

        Article article = articleRepository.findAll().getFirst();
        assertThat(article.getCategoryId()).isEqualTo(economyId);
        assertThat(article.getRssCategory()).isEqualTo("경제");
    }

    @Test
    void Item의_category가_매핑되지_않으면_Feed의_category로_매핑한다() {
        mapEconomy();
        RssItem item = item("guid-1", "https://news.example.com/1", "본문", List.of("연예"));

        articleCollectService.collect(feed(item), SERVICE_DATE);

        Article article = articleRepository.findAll().getFirst();
        assertThat(article.getCategoryId()).isEqualTo(economyId);
        assertThat(article.getRssCategory()).isEqualTo("경제");
    }

    @Test
    void 매핑되지_않는_기사는_카테고리_없이_저장한다() {
        RssItem item = item("guid-1", "https://news.example.com/1", "본문", List.of("연예"));

        articleCollectService.collect(feed(item), SERVICE_DATE);

        Article article = articleRepository.findAll().getFirst();
        assertThat(article.getCategoryId()).isNull();
        assertThat(article.getRssCategory()).isEqualTo("연예");
    }

    @Test
    void 같은_Feed를_다시_저장해도_기사가_중복_저장되지_않는다() {
        FetchedFeed feed = feed(item("guid-1", "https://news.example.com/1", "본문", List.of()),
            item(null, "https://news.example.com/2", "본문", List.of()));
        articleCollectService.collect(feed, SERVICE_DATE);

        int saved = articleCollectService.collect(feed, SERVICE_DATE);

        assertThat(saved).isZero();
        assertThat(articleRepository.count()).isEqualTo(2);
    }

    @Test
    void GUID나_URL이_이미_있는_기사는_건너뛴다() {
        articleCollectService.collect(
            feed(item("guid-1", "https://news.example.com/1", "본문", List.of())), SERVICE_DATE);

        int saved = articleCollectService.collect(feed(
            item("guid-1", "https://news.example.com/changed-url", "본문", List.of()),
            item("guid-new", "https://news.example.com/1", "본문", List.of()),
            item("guid-2", "https://news.example.com/2", "본문", List.of()),
            item("guid-2", "https://news.example.com/2-dup", "본문", List.of())), SERVICE_DATE);

        assertThat(saved).isEqualTo(1);
        assertThat(articleRepository.findAll()).extracting(Article::getGuid)
            .containsExactlyInAnyOrder("guid-1", "guid-2");
    }

    @Test
    void URL이_중복이라_건너뛴_기사의_GUID로_다른_신규_기사를_막지_않는다() {
        articleCollectService.collect(
            feed(item("guid-old", "https://news.example.com/1", "본문", List.of())), SERVICE_DATE);

        int saved = articleCollectService.collect(feed(
            item("guid-new", "https://news.example.com/1", "본문", List.of()),
            item("guid-new", "https://news.example.com/2", "본문", List.of())), SERVICE_DATE);

        assertThat(saved).isEqualTo(1);
        assertThat(articleRepository.findAll()).extracting(Article::getGuid, Article::getOriginalUrl)
            .containsExactlyInAnyOrder(
                tuple("guid-old", "https://news.example.com/1"),
                tuple("guid-new", "https://news.example.com/2"));
    }

    @Test
    void 컬럼_길이를_넘는_기사는_저장하지_않는다() {
        RssItem longTitle = new RssItem("guid-1", "https://news.example.com/1", "가".repeat(301), "본문",
            PUBLISHED_AT, List.of(), null);
        RssItem longUrl = item("guid-2", "https://news.example.com/" + "a".repeat(1000), "본문", List.of());
        RssItem longGuid = item("g".repeat(501), "https://news.example.com/3", "본문", List.of());
        RssItem ok = item("guid-4", "https://news.example.com/4", "본문", List.of("가".repeat(51)));

        int saved = articleCollectService.collect(feed(longTitle, longUrl, longGuid, ok), SERVICE_DATE);

        List<Article> articles = articleRepository.findAll();
        assertThat(saved).isEqualTo(1);
        assertThat(articles).extracting(Article::getGuid).containsExactly("guid-4");
        // 50자를 넘는 Item category는 버리고 Feed의 category를 남긴다
        assertThat(articles.getFirst().getRssCategory()).isEqualTo("경제");
    }

    @Test
    void 제목이_외국어이거나_깨진_기사는_저장하지_않는다() {
        RssItem foreign = new RssItem("guid-1", "https://news.example.com/1", "Lợi ích của sữa chua đối với người",
            "본문", PUBLISHED_AT, List.of(), null);
        RssItem broken = new RssItem("guid-2", "https://news.example.com/2", "경제 �� 뉴스", "본문",
            PUBLISHED_AT, List.of(), null);
        RssItem html = new RssItem("guid-3", "https://news.example.com/3", "경제 <b>속보</b> 뉴스", "본문",
            PUBLISHED_AT, List.of(), null);

        int saved = articleCollectService.collect(feed(foreign, broken, html,
            item("guid-4", "https://news.example.com/4", "본문", List.of())), SERVICE_DATE);

        assertThat(saved).isEqualTo(1);
        assertThat(articleRepository.findAll()).extracting(Article::getGuid).containsExactly("guid-4");
    }

    @Test
    void 사진_기사는_저장하지_않고_다음_순서_기사로_채운다() {
        mapEconomy();
        RssItem photo = new RssItem("guid-0", "https://news.example.com/0", "[사진]삼성 한국시리즈 4차전 선발투수",
            "본문", PUBLISHED_AT, List.of(), null);

        int saved = articleCollectService.collect(feed(photo, item("guid-1", "https://news.example.com/1", "본문",
            List.of())), SERVICE_DATE);

        assertThat(saved).isEqualTo(1);
        assertThat(articleRepository.findAll()).extracting(Article::getGuid).containsExactly("guid-1");
        verify(articleCrawler, never()).crawl("https://news.example.com/0");
    }

    @Test
    void 카테고리가_매핑된_기사는_원문을_크롤링해_원문_주소와_본문과_읽기시간을_저장한다() {
        mapEconomy();
        given(articleCrawler.crawl("https://news.example.com/1"))
            .willReturn(CrawlResult.success(new CrawledArticle("https://press.example.com/1", BODY)));

        articleCollectService.collect(feed(item("guid-1", "https://news.example.com/1", "요약", List.of())),
            SERVICE_DATE);

        Article article = articleRepository.findAll().getFirst();
        assertThat(article.getSourceUrl()).isEqualTo("https://press.example.com/1");
        assertThat(article.getOriginalUrl()).isEqualTo("https://news.example.com/1");
        assertThat(article.getRawContent()).isEqualTo("요약");
        // 1000자 ÷ 500자/분
        assertThat(article.getReadingMinutes()).isEqualByComparingTo(new BigDecimal("2.000"));
        assertThat(article.getProcessStatus()).isEqualTo(ArticleProcessStatus.CLEANED);
        assertThat(articleContentRepository.findAll())
            .extracting(ArticleContent::getArticleId, ArticleContent::getContent)
            .containsExactly(tuple(article.getId(), BODY));
    }

    @Test
    void 카테고리별로_Feed_순서가_앞선_기사부터_최대_수까지만_저장한다() {
        mapEconomy();

        int saved = articleCollectService.collect(feed(items(1, 25)), SERVICE_DATE);

        assertThat(saved).isEqualTo(MAX_PER_CATEGORY);
        assertThat(articleRepository.findAll()).extracting(Article::getGuid)
            .containsExactlyInAnyOrderElementsOf(guids(1, MAX_PER_CATEGORY));
        verify(articleCrawler, never()).crawl("https://news.example.com/21");
    }

    @Test
    void 원문을_가져오지_못한_기사는_저장하지_않고_다음_순서_기사로_채운다() {
        mapEconomy();
        given(articleCrawler.crawl("https://news.example.com/3")).willReturn(CrawlResult.fetchFailure());
        given(articleCrawler.crawl("https://news.example.com/7")).willReturn(CrawlResult.fetchFailure());

        int saved = articleCollectService.collect(feed(items(1, 25)), SERVICE_DATE);

        assertThat(saved).isEqualTo(MAX_PER_CATEGORY);
        List<String> expected = new ArrayList<>(guids(1, 22));
        expected.removeAll(List.of("guid-3", "guid-7"));
        assertThat(articleRepository.findAll()).extracting(Article::getGuid)
            .containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    void 원문을_하나도_가져오지_못하면_저장하지_않고_그_카테고리를_0건으로_알린다() {
        mapEconomy();
        given(articleCrawler.crawl(anyString())).willReturn(CrawlResult.fetchFailure());

        int saved = articleCollectService.collect(feed(items(1, 25)), SERVICE_DATE);

        assertThat(saved).isZero();
        CategoryFill fill = articleCollectService.checkCategoryFill(SERVICE_DATE);
        assertThat(fill.emptyCategoryIds()).containsExactly(economyId);
        assertThat(fill.shortage()).isEmpty();
    }

    @Test
    void 최대_수를_채우지_못한_카테고리는_저장한_수와_함께_알린다() {
        mapEconomy();
        Long societyId = insertCategory("사회", 2);
        categoryMappingRepository.save(CategoryMapping.create(feedId, "사회", societyId));

        articleCollectService.collect(feed(items(1, 5)), SERVICE_DATE);

        CategoryFill fill = articleCollectService.checkCategoryFill(SERVICE_DATE);
        assertThat(fill.shortage()).containsExactly(entry(economyId, 5L));
        assertThat(fill.emptyCategoryIds()).containsExactly(societyId);
    }

    @Test
    void 매핑이_없는_활성_카테고리도_0건으로_알리고_비활성_카테고리는_보지_않는다() {
        mapEconomy();
        Long unmappedId = insertCategory("과학·환경", 5);
        Long inactiveId = insertCategory("예전 카테고리", 9);
        jdbcTemplate.update("UPDATE category SET active = false WHERE id = ?", inactiveId);

        articleCollectService.collect(feed(items(1, 25)), SERVICE_DATE);

        CategoryFill fill = articleCollectService.checkCategoryFill(SERVICE_DATE);
        assertThat(fill.emptyCategoryIds()).containsExactly(unmappedId);
    }

    @Test
    void 최대_수를_채운_카테고리는_알리지_않는다() {
        mapEconomy();

        articleCollectService.collect(feed(items(1, 25)), SERVICE_DATE);

        CategoryFill fill = articleCollectService.checkCategoryFill(SERVICE_DATE);
        assertThat(fill.emptyCategoryIds()).isEmpty();
        assertThat(fill.shortage()).isEmpty();
    }

    @Test
    void 원문을_받았지만_기준에_맞지_않으면_저장하지_않는다() {
        mapEconomy();
        given(articleCrawler.crawl(anyString())).willReturn(CrawlResult.excluded());

        int saved = articleCollectService.collect(feed(items(1, 5)), SERVICE_DATE);

        assertThat(saved).isZero();
    }

    @Test
    void 일부만_원문을_가져오면_가져온_기사만_저장한다() {
        mapEconomy();
        given(articleCrawler.crawl(anyString())).willReturn(CrawlResult.fetchFailure());
        given(articleCrawler.crawl("https://news.example.com/3"))
            .willReturn(CrawlResult.success(new CrawledArticle("https://press.example.com/3", BODY)));

        int saved = articleCollectService.collect(feed(items(1, 25)), SERVICE_DATE);

        assertThat(saved).isEqualTo(1);
    }

    @Test
    void 후보가_모자라면_원문을_가져온_기사만_저장한다() {
        mapEconomy();
        given(articleCrawler.crawl("https://news.example.com/2")).willReturn(CrawlResult.fetchFailure());

        int saved = articleCollectService.collect(feed(items(1, 5)), SERVICE_DATE);

        assertThat(saved).isEqualTo(4);
    }

    @Test
    void 배치를_다시_돌리면_이미_저장한_수만큼_빼고_채운다() {
        mapEconomy();
        articleCollectService.collect(feed(items(1, 15)), SERVICE_DATE);

        int saved = articleCollectService.collect(feed(items(1, 30)), SERVICE_DATE);

        assertThat(saved).isEqualTo(MAX_PER_CATEGORY - 15);
        assertThat(articleRepository.findAll()).extracting(Article::getGuid)
            .containsExactlyInAnyOrderElementsOf(guids(1, MAX_PER_CATEGORY));
    }

    @Test
    void 다른_서비스일에_저장한_기사는_최대_수에_세지_않는다() {
        mapEconomy();
        articleCollectService.collect(feed(items(1, 20)), SERVICE_DATE.minusDays(1));

        int saved = articleCollectService.collect(feed(items(21, 40)), SERVICE_DATE);

        assertThat(saved).isEqualTo(MAX_PER_CATEGORY);
    }

    @Test
    void 카테고리가_매핑되지_않은_기사는_크롤링하지_않고_검수용으로_저장한다() {
        articleCollectService.collect(feed(item("guid-1", "https://news.example.com/1", "본문", List.of("연예"))),
            SERVICE_DATE);

        Article article = articleRepository.findAll().getFirst();
        assertThat(article.getProcessStatus()).isEqualTo(ArticleProcessStatus.COLLECTED);
        assertThat(article.getSourceUrl()).isNull();
        verify(articleCrawler, never()).crawl(anyString());
    }

    @Test
    void 구글_뉴스가_같은_사건으로_묶은_기사는_하나만_고른다() {
        mapEconomy();
        RssItem first = new RssItem("A1", GOOGLE + "A1", "코스피 급락에 개인 투자자 매수세", cluster("A1", "B9"),
            PUBLISHED_AT, List.of(), "한겨레");
        RssItem sameStory = new RssItem("A2", GOOGLE + "A2", "증시 하락장 버틴 개미들 순매수", cluster("A2", "B9"),
            PUBLISHED_AT, List.of(), "경향신문");
        RssItem other = new RssItem("A3", GOOGLE + "A3", "프로야구 한국시리즈 4차전 결과", cluster("A3"),
            PUBLISHED_AT, List.of(), "스포츠서울");

        int saved = articleCollectService.collect(feed(first, sameStory, other), SERVICE_DATE);

        assertThat(saved).isEqualTo(2);
        assertThat(articleRepository.findAll()).extracting(Article::getGuid).containsExactlyInAnyOrder("A1", "A3");
    }

    @Test
    void 구글이_묶지_않았어도_제목이_비슷하면_같은_사건으로_본다() {
        mapEconomy();
        RssItem first = new RssItem("A1", GOOGLE + "A1", "테슬라 사이버트럭 2만대 판매 그쳐 생산 축소", null,
            PUBLISHED_AT, List.of(), null);
        RssItem similar = new RssItem("A2", GOOGLE + "A2", "[단독] 테슬라 사이버트럭 판매 2만대 그쳐…생산 축소", null,
            PUBLISHED_AT, List.of(), null);

        int saved = articleCollectService.collect(feed(first, similar), SERVICE_DATE);

        assertThat(saved).isEqualTo(1);
        assertThat(articleRepository.findAll()).extracting(Article::getGuid).containsExactly("A1");
    }

    @Test
    void 다른_카테고리에서_이미_고른_사건은_고르지_않는다() {
        mapEconomy();
        Long techFeedId = rssFeedRepository.save(RssFeed.create("구글 뉴스", "https://news.example.com/tech.xml", "IT"))
            .getId();
        Long techId = insertCategory("IT", 2);
        categoryMappingRepository.save(CategoryMapping.create(techFeedId, "IT", techId));
        articleCollectService.collect(feed(new RssItem("A1", GOOGLE + "A1", "엔비디아 네이버 3대 주주 됐다",
            cluster("A1", "C7"), PUBLISHED_AT, List.of(), null)), SERVICE_DATE);

        int saved = articleCollectService.collect(new FetchedFeed(techFeedId, "구글 뉴스", "IT", List.of(
            new RssItem("T1", GOOGLE + "T1", "젠슨 황의 선택은 국내 포털", cluster("T1", "C7"), PUBLISHED_AT, List.of(),
                null),
            new RssItem("T2", GOOGLE + "T2", "반도체 수출 석 달 연속 증가", cluster("T2"), PUBLISHED_AT, List.of(),
                null))), SERVICE_DATE);

        assertThat(saved).isEqualTo(1);
        assertThat(articleRepository.findAll()).extracting(Article::getGuid).containsExactlyInAnyOrder("A1", "T2");
    }

    @Test
    void 같은_사건의_앞_기사가_크롤링에_실패하면_뒤_기사를_대신_고른다() {
        mapEconomy();
        given(articleCrawler.crawl(GOOGLE + "A1")).willReturn(CrawlResult.fetchFailure());
        RssItem failed = new RssItem("A1", GOOGLE + "A1", "코스피 급락에 개인 투자자 매수세", cluster("A1", "B9"),
            PUBLISHED_AT, List.of(), null);
        RssItem sameStory = new RssItem("A2", GOOGLE + "A2", "증시 하락장 버틴 개미들 순매수", cluster("A2", "B9"),
            PUBLISHED_AT, List.of(), null);

        int saved = articleCollectService.collect(feed(failed, sameStory), SERVICE_DATE);

        assertThat(saved).isEqualTo(1);
        assertThat(articleRepository.findAll()).extracting(Article::getGuid).containsExactly("A2");
    }

    @Test
    void 원문_주소가_같은_기사는_하나만_저장한다() {
        mapEconomy();
        articleCollectService.collect(feed(item("guid-1", "https://news.example.com/1", "본문", List.of())),
            SERVICE_DATE.minusDays(1));
        String savedSource = articleRepository.findAll().getFirst().getSourceUrl();
        given(articleCrawler.crawl("https://news.example.com/2"))
            .willReturn(CrawlResult.success(new CrawledArticle(savedSource, BODY)));
        given(articleCrawler.crawl("https://news.example.com/3"))
            .willReturn(CrawlResult.success(new CrawledArticle("https://press.example.com/same", BODY)));
        given(articleCrawler.crawl("https://news.example.com/4"))
            .willReturn(CrawlResult.success(new CrawledArticle("https://press.example.com/same", BODY)));

        int saved = articleCollectService.collect(feed(items(2, 5)), SERVICE_DATE);

        assertThat(saved).isEqualTo(2);
        assertThat(articleRepository.findAll()).extracting(Article::getGuid)
            .containsExactlyInAnyOrder("guid-1", "guid-3", "guid-5");
    }

    private void mapEconomy() {
        categoryMappingRepository.save(CategoryMapping.create(feedId, "경제", economyId));
    }

    private Long insertCategory(String name, int displayOrder) {
        return jdbcTemplate.queryForObject(
            "INSERT INTO category (name, display_order) VALUES (?, ?) RETURNING id", Long.class, name, displayOrder);
    }

    private FetchedFeed feed(RssItem... items) {
        return new FetchedFeed(feedId, "한국경제", "경제", List.of(items));
    }

    private RssItem item(String guid, String link, String content, List<String> categories) {
        return new RssItem(guid, link, title(guid == null ? link : guid), content, PUBLISHED_AT, categories, null);
    }

    // Feed 순서대로 guid-from ~ guid-to
    private RssItem[] items(int from, int to) {
        return IntStream.rangeClosed(from, to)
            .mapToObj(i -> item("guid-" + i, "https://news.example.com/" + i, "요약", List.of()))
            .toArray(RssItem[]::new);
    }

    private List<String> guids(int from, int to) {
        return IntStream.rangeClosed(from, to).mapToObj(i -> "guid-" + i).toList();
    }

    // 기사마다 서로 다른 사건으로 보이도록 겹치지 않는 한글 제목을 만든다
    private static String title(String seed) {
        int base = Math.floorMod(seed.hashCode(), 2000) * 5;
        StringBuilder title = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            title.appendCodePoint(0xAC00 + (base + i * 1009) % 11172);
        }
        return title.toString();
    }

    // 구글 뉴스 description: 같은 사건으로 묶인 기사 링크 목록
    private static String cluster(String... articleIds) {
        StringBuilder html = new StringBuilder("<ol>");
        for (String id : articleIds) {
            html.append("<li><a href=\"").append(GOOGLE).append(id).append("?oc=5\">기사</a></li>");
        }
        return html.append("</ol>").toString();
    }
}
