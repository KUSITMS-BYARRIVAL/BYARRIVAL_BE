package com.byarrival.domain.article.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ArticleContentCleanerTest {

    private final ArticleContentCleaner cleaner = new ArticleContentCleaner();

    @Test
    void HTML_태그를_지우고_문단은_줄바꿈으로_남긴다() {
        String raw = "<div class=\"article\"><p>한국은행이 <b>기준금리</b>를 동결했다.</p><p>시장은<br/>예상대로라는 반응이다.</p></div>";

        assertThat(cleaner.clean(raw)).contains("한국은행이 기준금리를 동결했다.\n시장은\n예상대로라는 반응이다.");
    }

    @Test
    void 스크립트_스타일_주석은_내용까지_지운다() {
        String raw = "<style>.ad{color:red}</style>본문<script type=\"text/javascript\">alert('x')</script><!-- 광고 -->끝";

        assertThat(cleaner.clean(raw)).contains("본문끝");
    }

    @Test
    void HTML_엔티티를_디코딩한다() {
        String raw = "A&amp;B &quot;인용&quot; &middot; &#8220;숫자&#x201D;&nbsp;끝";

        assertThat(cleaner.clean(raw)).contains("A&B \"인용\" · “숫자” 끝");
    }

    @Test
    void 본문에_쓰인_꺾쇠_글자는_태그로_지우지_않는다() {
        assertThat(cleaner.clean("&lt;b&gt;는 굵은 글씨 태그다")).contains("<b>는 굵은 글씨 태그다");
    }

    @Test
    void 모르는_엔티티는_그대로_둔다() {
        assertThat(cleaner.clean("가 &unknown; 나")).contains("가 &unknown; 나");
    }

    @Test
    void 연속_공백과_빈_줄을_정리한다() {
        String raw = "  첫 줄 \t  이어서 \r\n\r\n\n   둘째 줄​  ";

        assertThat(cleaner.clean(raw)).contains("첫 줄 이어서\n둘째 줄");
    }

    @Test
    void 원본이_없으면_정제_결과가_없다() {
        assertThat(cleaner.clean(null)).isEmpty();
        assertThat(cleaner.clean("   ")).isEmpty();
    }

    @Test
    void 태그만_있으면_정제_결과가_없다() {
        assertThat(cleaner.clean("<p><img src=\"a.jpg\"/></p><br>")).isEmpty();
    }
}
