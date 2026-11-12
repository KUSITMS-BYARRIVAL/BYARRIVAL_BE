package com.byarrival.domain.article.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.byarrival.domain.article.config.ArticleCrawlProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.util.unit.DataSize;

class ArticleTextInspectorTest {

    private final ArticleTextInspector inspector = new ArticleTextInspector(new ArticleCrawlProperties(
        Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofSeconds(5), DataSize.ofMegabytes(1), 3, 1, 200, 0.5,
        "test-agent"));

    @ParameterizedTest
    @ValueSource(strings = {
        "\"삼전닉스 믿고 버텼는데\"…증권가가 내놓은 뜻밖의 조언 [종목+]",
        "[단독] 320만 아파트 입주민 쓰는 '아파트너'도 개인정보 유출",
        "SK하이닉스, HBM4 양산 돌입",
        "Discord, Electron을 버리고 RAM 사용량이 적은 새 앱으로 전환",
        "코스피 12% 급락…골드만삭스 \"중간선거 영향\""
    })
    void 한국어_제목은_읽을_수_있다(String title) {
        assertThat(inspector.isReadableTitle(title)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "Lợi ích của sữa chua đối với người mỡ máu cao",
        "NVIDIA GTC 2026 keynote recap",
        "Developmental Delays Linked to Retinopathy of Prematurity 미숙아",
        "경제 �� 뉴스",
        "경제 <b>속보</b> 뉴스",
        "경제 &amp; 산업",
        "경제\u0007뉴스",
        " "
    })
    void 외국어이거나_깨진_제목은_읽을_수_없다(String title) {
        assertThat(inspector.isReadableTitle(title)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "[사진]삼성 한국시리즈 4차전 선발투수 장찬희",
        "[포토] 가을 밤바다 수놓은 불꽃",
        "【화보】 배우 김OO, 시사회 참석",
        "시사회 레드카펫 [포토뉴스]",
        "(사진) 첫눈 내린 서울",
        "[Photo] 부산 불꽃축제"
    })
    void 사진_말머리가_있으면_사진_기사다(String title) {
        assertThat(inspector.isPhotoArticle(title)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "[단독] 사진 한 장으로 드러난 진실",
        "포토라인 선 전 장관 \"성실히 조사받겠다\"",
        "[KS4] 선발에서 롱릴리프로…삼성 박진만 감독"
    })
    void 사진_말머리가_아니면_사진_기사가_아니다(String title) {
        assertThat(inspector.isPhotoArticle(title)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "[오늘의 운세] 2026년 11월 12일 오행별 행운의 컬러",
        "[띠별 운세] 11월 13일",
        "[미리보는 이데일리 신문] AI,이젠 기술보다 생산성 싸움",
        "지수와 눈맞춤·이부진과 손하트…'디올의 여자들' [영상]",
        "출근길 빙판 주의 (영상)",
        "[부고] 김OO씨 별세",
        "【인사】 기획재정부"
    })
    void 뉴스가_아닌_말머리가_있으면_거른다(String title) {
        assertThat(inspector.isNonNewsArticle(title)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "[인사이트] 반도체 슈퍼사이클의 끝",
        "[단독] 영상 속 남성 신원 확인",
        "인사 청문회 앞둔 후보자",
        "운세 앱 이용자 1천만 돌파",
        "[KS4] 선발에서 롱릴리프로…삼성 박진만 감독"
    })
    void 뉴스가_아닌_말머리가_아니면_거르지_않는다(String title) {
        assertThat(inspector.isNonNewsArticle(title)).isFalse();
    }

    @Test
    void 숫자와_기호는_한글_비율에_세지_않는다() {
        assertThat(ArticleTextInspector.hangulRatio("코스피 12% 급락, 3,000선 붕괴!")).isEqualTo(1.0);
        assertThat(ArticleTextInspector.hangulRatio("2026 / 12")).isZero();
    }

    @Test
    void 외국어는_이어진_단어를_하나로_센다() {
        // AI(1) + 반도체(3)
        assertThat(ArticleTextInspector.hangulRatio("AI 반도체")).isEqualTo(0.75);
        // Electron을: Electron(1) + 을(1)
        assertThat(ArticleTextInspector.hangulRatio("Electron을")).isEqualTo(0.5);
    }

    @Test
    void 한자와_가나는_글자마다_센다() {
        assertThat(ArticleTextInspector.hangulRatio("中国经济增长放缓 한국")).isEqualTo(0.2);
        assertThat(ArticleTextInspector.hangulRatio("日本の株価が上昇 한국")).isEqualTo(0.2);
        assertThat(ArticleTextInspector.hangulRatio("美 증시 급락")).isEqualTo(0.8);
    }
}
