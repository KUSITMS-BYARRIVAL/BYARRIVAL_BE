package com.byarrival.domain.article.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ArticleBodyExtractorTest {

    private static final String URL = "https://press.example.com/article/1";
    private static final String PARAGRAPH = "정부가 내년 예산안을 국회에 제출했다. 총지출은 올해보다 늘었다.";

    private final ArticleBodyExtractor extractor = new ArticleBodyExtractor(JsonMapper.builder().build());
    private final ArticleContentCleaner cleaner = new ArticleContentCleaner();

    @Test
    void schema_org_본문_표시가_있으면_그_영역을_본문으로_쓴다() {
        String html = page("""
            <div class="ad">%s%s%s</div>
            <div itemprop="articleBody"><div class="p">%s</div><div class="p">둘째 문단입니다.</div></div>
            """.formatted(PARAGRAPH, PARAGRAPH, PARAGRAPH, PARAGRAPH));

        assertThat(text(html)).contains(PARAGRAPH + "\n둘째 문단입니다.");
    }

    @Test
    void 표시가_없으면_직접_담은_글자가_가장_많은_블록을_본문으로_쓴다() {
        String html = page("""
            <div class="menu"><a href="/1">정치</a> <a href="/2">경제</a> <a href="/3">사회</a></div>
            <div class="article">%s<br>%s<p>%s</p></div>
            <div class="related"><p><a href="/4">%s</a></p></div>
            """.formatted(PARAGRAPH, PARAGRAPH, PARAGRAPH, PARAGRAPH));

        assertThat(text(html)).isEqualTo(String.join("\n", PARAGRAPH, PARAGRAPH, PARAGRAPH));
    }

    @Test
    void 링크가_대부분인_블록은_본문으로_고르지_않는다() {
        String links = ("<p><a href=\"/x\">" + PARAGRAPH + "</a></p>").repeat(5);
        String html = page("<div class=\"related\">" + links + "</div><div class=\"article\"><p>" + PARAGRAPH + "</p></div>");

        assertThat(text(html)).isEqualTo(PARAGRAPH);
    }

    @Test
    void 페이지_전체를_form으로_감싸도_본문을_찾는다() {
        String html = page("<form id=\"form1\"><div id=\"content\"><p>" + PARAGRAPH + "</p></div></form>");

        assertThat(text(html)).isEqualTo(PARAGRAPH);
    }

    @Test
    void 스크립트와_사진_설명은_본문에서_뺀다() {
        String html = page("""
            <div class="article"><p>%s</p><script>var a = '광고';</script>
            <figure><img src="a.jpg"><figcaption>사진 설명</figcaption></figure></div>
            """.formatted(PARAGRAPH));

        assertThat(text(html)).isEqualTo(PARAGRAPH);
    }

    @Test
    void 본문을_스크립트_JSON으로_내려주는_Arc_XP_페이지는_text_문단을_쓴다() {
        String html = page("""
            <div class="author">기자 소개 문구입니다.</div>
            <script>window.Fusion={};Fusion.globalContent={"content_elements":[
              {"type":"text","content":"첫 문단 <b>강조</b>"},{"type":"image","url":"a.jpg"},
              {"type":"text","content":"둘째 문단"}]};Fusion.globalContentConfig={};</script>
            """);

        assertThat(text(html)).isEqualTo("첫 문단 강조\n둘째 문단");
    }

    @Test
    void meta_태그의_charset으로_읽는다() {
        Charset eucKr = Charset.forName("EUC-KR");
        byte[] html = ("<html><head><meta charset=\"euc-kr\"></head><body><div><p>" + PARAGRAPH
            + "</p></div></body></html>").getBytes(eucKr);

        assertThat(extractor.extract(html, URL).flatMap(cleaner::clean)).contains(PARAGRAPH);
    }

    @Test
    void meta_태그가_없으면_응답_헤더의_charset으로_읽는다() {
        byte[] html = ("<html><body><div><p>" + PARAGRAPH + "</p></div></body></html>")
            .getBytes(Charset.forName("EUC-KR"));

        assertThat(extractor.extract(html, "EUC-KR", URL).flatMap(cleaner::clean)).contains(PARAGRAPH);
    }

    @Test
    void 글자가_없는_페이지는_본문을_찾지_못한다() {
        assertThat(extractor.extract(page("<div><img src=\"a.jpg\"></div>").getBytes(StandardCharsets.UTF_8), URL))
            .isEmpty();
    }

    private String text(String html) {
        Optional<String> body = extractor.extract(html.getBytes(StandardCharsets.UTF_8), URL);
        return body.flatMap(cleaner::clean).orElse("");
    }

    private String page(String body) {
        return "<html><head><meta charset=\"utf-8\"></head><body>" + body + "</body></html>";
    }
}
