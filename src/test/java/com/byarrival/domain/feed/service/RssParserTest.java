package com.byarrival.domain.feed.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.domain.feed.dto.RssItem;
import com.byarrival.domain.feed.exception.FeedErrorCode;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class RssParserTest {

    private static final Long FEED_ID = 1L;

    private final RssParser rssParser = new RssParser();

    @Test
    void RSS_Item을_정규화_기사로_바꾼다() {
        List<RssItem> items = parse(rss("""
            <item>
              <title>기준금리 동결</title>
              <link>https://news.example.com/articles/1</link>
              <guid isPermaLink="false">news-1</guid>
              <description><![CDATA[<p>요약</p>]]></description>
              <pubDate>Fri, 06 Nov 2026 10:00:00 +0900</pubDate>
              <category>경제</category>
              <category>금융</category>
            </item>
            """));

        assertThat(items).containsExactly(new RssItem("news-1", "https://news.example.com/articles/1", "기준금리 동결",
            "<p>요약</p>", LocalDateTime.of(2026, 11, 6, 10, 0), List.of("경제", "금융"), null));
    }

    @Test
    void source를_언론사로_읽고_제목_끝의_언론사명을_뗀다() {
        List<RssItem> items = parse(rss("""
            <item>
              <title>기준금리 동결 - 한겨레</title>
              <link>https://news.google.com/rss/articles/abc?oc=5</link>
              <pubDate>Fri, 06 Nov 2026 01:00:00 GMT</pubDate>
              <source url="https://www.hani.co.kr">한겨레</source>
            </item>
            """));

        assertThat(items.getFirst().source()).isEqualTo("한겨레");
        assertThat(items.getFirst().title()).isEqualTo("기준금리 동결");
    }

    @Test
    void 제목_끝에_언론사명이_겹쳐_있으면_모두_뗀다() {
        List<RssItem> items = parse(rss("""
            <item>
              <title>인텔 아태 부사장 "소버린 AI도 경쟁력" - 머니투데이 - 머니투데이</title>
              <link>https://news.google.com/rss/articles/abc?oc=5</link>
              <pubDate>Fri, 06 Nov 2026 01:00:00 GMT</pubDate>
              <source url="https://www.mt.co.kr">머니투데이</source>
            </item>
            """));

        assertThat(items.getFirst().title()).isEqualTo("인텔 아태 부사장 \"소버린 AI도 경쟁력\"");
    }

    @Test
    void source가_사이트_주소여도_제목_끝의_주소를_뗀다() {
        List<RssItem> items = parse(rss("""
            <item>
              <title>신생 바이오 토르테라퓨틱스는 뭘 하는 곳? - https://www.dhtimes.co.kr/</title>
              <link>https://news.google.com/rss/articles/abc?oc=5</link>
              <pubDate>Fri, 06 Nov 2026 01:00:00 GMT</pubDate>
              <source url="https://www.dhtimes.co.kr">https://www.dhtimes.co.kr/</source>
            </item>
            """));

        assertThat(items.getFirst().title()).isEqualTo("신생 바이오 토르테라퓨틱스는 뭘 하는 곳?");
    }

    @Test
    void 제목_끝이_source와_다르면_제목을_그대로_둔다() {
        List<RssItem> items = parse(rss("""
            <item>
              <title>A - B 협상 타결</title>
              <link>https://news.google.com/rss/articles/abc?oc=5</link>
              <pubDate>Fri, 06 Nov 2026 01:00:00 GMT</pubDate>
              <source url="https://www.hani.co.kr">한겨레</source>
            </item>
            """));

        assertThat(items.getFirst().title()).isEqualTo("A - B 협상 타결");
    }

    @Test
    void content_encoded가_있으면_description보다_먼저_쓴다() {
        List<RssItem> items = parse(rss("""
            <item>
              <title>제목</title>
              <link>https://news.example.com/articles/1</link>
              <description>발췌</description>
              <content:encoded><![CDATA[<p>본문 전체</p>]]></content:encoded>
              <pubDate>Fri, 06 Nov 2026 10:00:00 +0900</pubDate>
            </item>
            """));

        assertThat(items.getFirst().content()).isEqualTo("<p>본문 전체</p>");
    }

    @Test
    void 다른_시간대의_발행일은_KST로_바꾼다() {
        List<RssItem> items = parse(rss(item("<pubDate>Fri, 06 Nov 2026 01:00:00 GMT</pubDate>")));

        assertThat(items.getFirst().publishedAt()).isEqualTo(LocalDateTime.of(2026, 11, 6, 10, 0));
    }

    @Test
    void pubDate가_없으면_dc_date를_발행일로_쓴다() {
        List<RssItem> items = parse(rss(item("<dc:date>2026-11-06T01:00:00Z</dc:date>")));

        assertThat(items.getFirst().publishedAt()).isEqualTo(LocalDateTime.of(2026, 11, 6, 10, 0));
    }

    @Test
    void 시간대가_없는_발행일은_KST로_본다() {
        List<RssItem> items = parse(rss(item("<pubDate>2026-11-06 10:00:00</pubDate>")));

        assertThat(items.getFirst().publishedAt()).isEqualTo(LocalDateTime.of(2026, 11, 6, 10, 0));
    }

    @Test
    void KST로_표기한_발행일을_읽는다() {
        List<RssItem> items = parse(rss(item("<pubDate>Fri, 06 Nov 2026 10:00:00 KST</pubDate>")));

        assertThat(items.getFirst().publishedAt()).isEqualTo(LocalDateTime.of(2026, 11, 6, 10, 0));
    }

    @Test
    void link가_없으면_permalink_guid를_원문_URL로_쓴다() {
        List<RssItem> items = parse(rss("""
            <item>
              <title>제목</title>
              <guid>https://news.example.com/articles/1</guid>
              <pubDate>Fri, 06 Nov 2026 10:00:00 +0900</pubDate>
            </item>
            """));

        assertThat(items.getFirst().link()).isEqualTo("https://news.example.com/articles/1");
    }

    @Test
    void 필수값이_빠진_Item은_제외하고_나머지는_읽는다() {
        List<RssItem> items = parse(rss("""
            <item>
              <link>https://news.example.com/no-title</link>
              <pubDate>Fri, 06 Nov 2026 10:00:00 +0900</pubDate>
            </item>
            <item>
              <title>URL 없음</title>
              <pubDate>Fri, 06 Nov 2026 10:00:00 +0900</pubDate>
            </item>
            <item>
              <title>발행일 없음</title>
              <link>https://news.example.com/no-date</link>
            </item>
            <item>
              <title>발행일 형식 오류</title>
              <link>https://news.example.com/bad-date</link>
              <pubDate>어제</pubDate>
            </item>
            <item>
              <title>http가 아닌 URL</title>
              <link>javascript:alert(1)</link>
              <pubDate>Fri, 06 Nov 2026 10:00:00 +0900</pubDate>
            </item>
            """ + item("<pubDate>Fri, 06 Nov 2026 10:00:00 +0900</pubDate>")));

        assertThat(items).extracting(RssItem::title).containsExactly("정상 기사");
    }

    @Test
    void 기사가_0건인_정상_RSS는_빈_목록이다() {
        assertThat(parse(rss(""))).isEmpty();
    }

    @Test
    void 문서에_선언된_인코딩으로_읽는다() {
        String xml = """
            <?xml version="1.0" encoding="EUC-KR"?>
            <rss version="2.0"><channel>%s</channel></rss>
            """.formatted(item("<pubDate>Fri, 06 Nov 2026 10:00:00 +0900</pubDate>"));

        List<RssItem> items = rssParser.parse(FEED_ID, xml.getBytes(Charset.forName("EUC-KR")));

        assertThat(items.getFirst().title()).isEqualTo("정상 기사");
    }

    @Test
    void XML이_아니면_파싱_실패다() {
        assertParseFailed("<html><body>점검 중입니다<br></body></html>");
    }

    @Test
    void RSS가_아닌_XML이면_파싱_실패다() {
        assertParseFailed("<html><body><p>Service Unavailable</p></body></html>");
        assertParseFailed("<feed xmlns=\"http://www.w3.org/2005/Atom\"></feed>");
    }

    @Test
    void channel이_없는_rss_문서는_파싱_실패다() {
        assertParseFailed("<rss version=\"2.0\"/>");
        assertParseFailed("<rss version=\"2.0\">" + item("<pubDate>Fri, 06 Nov 2026 10:00:00 +0900</pubDate>") + "</rss>");
    }

    @Test
    void 지원하지_않는_RSS_버전은_파싱_실패다() {
        assertParseFailed("<rss version=\"3.0\"><channel></channel></rss>");
    }

    @Test
    void version이_없어도_channel이_있으면_읽는다() {
        String xml = "<rss><channel>" + item("<pubDate>Fri, 06 Nov 2026 10:00:00 +0900</pubDate>") + "</channel></rss>";

        assertThat(parse(xml)).extracting(RssItem::title).containsExactly("정상 기사");
    }

    @Test
    void 빈_응답이면_파싱_실패다() {
        assertParseFailed("");
    }

    @Test
    void 외부_엔티티를_읽지_않는다() {
        assertParseFailed("""
            <?xml version="1.0"?>
            <!DOCTYPE rss [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
            <rss version="2.0"><channel><item><title>&xxe;</title></item></channel></rss>
            """);
    }

    private void assertParseFailed(String xml) {
        assertThatThrownBy(() -> rssParser.parse(FEED_ID, xml.getBytes(StandardCharsets.UTF_8)))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(FeedErrorCode.FEED_PARSE_FAILED);
    }

    private List<RssItem> parse(String xml) {
        return rssParser.parse(FEED_ID, xml.getBytes(StandardCharsets.UTF_8));
    }

    private String item(String dateElement) {
        return """
            <item>
              <title>정상 기사</title>
              <link>https://news.example.com/articles/ok</link>
              %s
            </item>
            """.formatted(dateElement);
    }

    private String rss(String items) {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0"
                 xmlns:content="http://purl.org/rss/1.0/modules/content/"
                 xmlns:dc="http://purl.org/dc/elements/1.1/">
              <channel>
                <title>테스트 언론사</title>
                %s
              </channel>
            </rss>
            """.formatted(items);
    }
}
