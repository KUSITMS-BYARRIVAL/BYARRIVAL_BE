package com.byarrival.domain.article.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.byarrival.domain.article.config.ArticleSelectionProperties;
import com.byarrival.domain.article.service.ArticleStoryMatcher.Story;
import org.junit.jupiter.api.Test;

class ArticleStoryMatcherTest {

    private static final String GOOGLE = "https://news.google.com/rss/articles/";

    private final ArticleStoryMatcher matcher = new ArticleStoryMatcher(new ArticleSelectionProperties(20, 0.37));

    @Test
    void 구글_뉴스_묶음에_같은_기사가_있으면_같은_사건이다() {
        Story a = matcher.storyOf(GOOGLE + "A1?oc=5", cluster("A1", "X9"), "젠슨 황, 중국 스파이 의심 여성과 연인 관계");
        Story b = matcher.storyOf(GOOGLE + "B1?oc=5", cluster("B1", "X9"), "미 정보당국 메모 공개");

        assertThat(matcher.isSameStory(a, b)).isTrue();
    }

    @Test
    void 한쪽_묶음에_다른_기사가_들어_있어도_같은_사건이다() {
        Story a = matcher.storyOf(GOOGLE + "A1", cluster("A1"), "첫째 기사 제목");
        Story b = matcher.storyOf(GOOGLE + "B1", cluster("B1", "A1"), "둘째 기사 다른 제목");

        assertThat(matcher.isSameStory(a, b)).isTrue();
    }

    @Test
    void 묶음이_없어도_제목이_비슷하면_같은_사건이다() {
        Story a = matcher.storyOf(GOOGLE + "A1", null, "지드래곤도 탔는데…'2만대' 팔린 테슬라 사이버트럭 결국");
        Story b = matcher.storyOf(GOOGLE + "B1", null, "年 50만대 판다더니 고작 2만대…테슬라, 사이버트럭 생산 축소");

        assertThat(matcher.isSameStory(a, b)).isTrue();
    }

    @Test
    void 말투만_비슷한_다른_사건은_같은_사건이_아니다() {
        Story a = matcher.storyOf(GOOGLE + "A1", null, "세브란스병원 의사에 흉기 휘두른 50대 여성 구속…법원 \"도망 염려\"");
        Story b = matcher.storyOf(GOOGLE + "B1", null, "'은평구 스토킹 살인' 60대 남성 구속…법원 \"도망할 염려\"");

        assertThat(matcher.isSameStory(a, b)).isFalse();
    }

    @Test
    void 말머리는_제목_비교에서_뺀다() {
        assertThat(ArticleStoryMatcher.titleBigrams("[단독] 반도체 (종합)"))
            .isEqualTo(ArticleStoryMatcher.titleBigrams("반도체"));
    }

    @Test
    void 구글_링크가_아니면_링크로_같은_기사를_판단한다() {
        Story a = matcher.storyOf("https://press.example.com/1", null, "첫째 기사 제목");
        Story b = matcher.storyOf("https://press.example.com/1", null, "전혀 다른 문구");

        assertThat(matcher.isSameStory(a, b)).isTrue();
    }

    private static String cluster(String... ids) {
        StringBuilder html = new StringBuilder("<ol>");
        for (String id : ids) {
            html.append("<li><a href=\"").append(GOOGLE).append(id).append("?oc=5\" target=\"_blank\">기사</a>")
                .append("&nbsp;&nbsp;<font color=\"#6f6f6f\">언론사</font></li>");
        }
        return html.append("</ol>").toString();
    }
}
