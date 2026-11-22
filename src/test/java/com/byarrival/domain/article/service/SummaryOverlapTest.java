package com.byarrival.domain.article.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SummaryOverlapTest {

    @Test
    void 제목을_되풀이한_문장은_높게_새_사실을_담은_문장은_낮게_잰다() {
        // 11-22 배치 3120: s3가 제목의 임시열차 15편 투입을 되풀이했다
        Map<String, Double> scores = SummaryOverlap.scores("경부선 화물열차 탈선 여파…코레일, 임시열차 15편 투입",
            List.of("경부선 화물열차 탈선으로 운행이 중단됐습니다.", "화물열차 21량 중 5량이 탈선했습니다.", "임시열차 15편을 추가로 투입했습니다."));

        // s3는 조각 12개 중 7개가 제목에 있고, s2는 "화물열차·탈선"만 겹쳐 12개 중 4개다
        assertThat(scores.get("title-s3")).isCloseTo(7.0 / 12, within(0.001));
        assertThat(scores.get("title-s2")).isCloseTo(4.0 / 12, within(0.001));
    }

    @Test
    void 공백과_문장_부호와_어미만_다르면_같은_문장으로_본다() {
        assertThat(SummaryOverlap.score("임시열차 15편을 투입했습니다.", "임시열차15편을투입")).isEqualTo(1.0);
    }

    @Test
    void 어미만_같은_서로_다른_문장은_겹치지_않는다고_본다() {
        assertThat(SummaryOverlap.score("금리를 동결했습니다.", "환율이 올랐습니다.")).isZero();
    }

    @Test
    void 비교하는_쌍과_가장_높은_쌍을_한_줄로_남긴다() {
        String line = SummaryOverlap.describe(SummaryOverlap.scores("가나다라",
            List.of("가나다라입니다.", "가나다라입니다.", "마바사아입니다.")));

        assertThat(line).isEqualTo(
            "max=1.00(title-s2) title-s2=1.00 title-s3=0.00 s1-s2=1.00 s1-s3=0.00 s2-s3=0.00");
    }

    @Test
    void 문장이_3개가_아니면_재지_않는다() {
        assertThat(SummaryOverlap.scores("제목", List.of("한 문장입니다."))).isEmpty();
        assertThat(SummaryOverlap.describe(Map.of())).isEmpty();
    }
}
