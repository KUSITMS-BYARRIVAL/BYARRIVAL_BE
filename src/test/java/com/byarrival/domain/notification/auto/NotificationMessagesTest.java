package com.byarrival.domain.notification.auto;

import static org.assertj.core.api.Assertions.assertThat;

import com.byarrival.domain.notification.auto.NotificationMessages.PopularScope;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class NotificationMessagesTest {

    private final NotificationMessages messages = new NotificationMessages(new Random(1));

    @Test
    void 제목은_후보_중에서_고른다() {
        Set<String> titles = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            titles.add(messages.routeReadyTitle());
        }

        assertThat(titles).containsExactlyInAnyOrderElementsOf(NotificationMessages.ROUTE_READY_TITLES);
    }

    @Test
    void 남은_정거장이_없으면_남은_수를_말하는_제목은_고르지_않는다() {
        for (int i = 0; i < 50; i++) {
            assertThat(messages.routeIncompleteTitle(0)).isIn(NotificationMessages.ROUTE_INCOMPLETE_TITLES);
        }
        Set<String> titles = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            titles.add(messages.routeIncompleteTitle(2));
        }
        assertThat(titles).contains("아직 2정거장 남았어요 🚉");
    }

    @Test
    void 연속_달성_본문은_최장_기록과_같거나_넘으면_최장_기록이라고_알린다() {
        assertThat(messages.streakMilestoneBody(7, 7)).isEqualTo("최장 기록이에요!");
        assertThat(messages.streakMilestoneBody(7, 12)).isEqualTo("최장 기록 12일까지 5일 남았어요.");
    }

    @Test
    void 스트릭_이탈_제목은_오늘_도착하면_이어지는_일수와_지금까지의_일수를_쓴다() {
        Set<String> titles = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            titles.add(messages.streakBreakTitle(3));
        }

        assertThat(titles).contains("오늘도 도착하면 4일 연속이에요 🔥", "4일째 출근길, 오늘도 같이 가요! 🥹",
            "3일 연속 탑승 기록, 여기서 멈추기엔 아깝잖아요 👀");
    }

    @Test
    void 인기_기사_제목은_길면_줄이고_본문은_모은_범위에_맞춘다() {
        assertThat(messages.popularTitle("가".repeat(150))).hasSize(NotificationMessages.MAX_TITLE_LENGTH).endsWith("…");
        assertThat(messages.popularBody(PopularScope.REGION_AND_JOB, "강남구", "디자인"))
            .isEqualTo("지금 강남구 디자인에서 많이 보고 있어요.");
        assertThat(messages.popularBody(PopularScope.JOB, "강남구", "디자인")).isEqualTo("오늘 디자인 직군에서 많이 보고 있어요.");
        assertThat(messages.popularBody(PopularScope.REGION, "강남구", null)).isEqualTo("지금 강남구 출근길에서 많이 보고 있어요.");
    }
}
