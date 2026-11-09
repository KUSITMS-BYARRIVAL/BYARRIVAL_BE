package com.byarrival.domain.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.byarrival.domain.auth.dto.response.TokenResponse;
import com.byarrival.domain.auth.service.TokenService;
import com.byarrival.domain.notification.dto.NotificationRequest;
import com.byarrival.domain.notification.entity.DeviceToken;
import com.byarrival.domain.notification.entity.Notification;
import com.byarrival.domain.notification.entity.NotificationType;
import com.byarrival.domain.notification.entity.PushStatus;
import com.byarrival.domain.notification.repository.DeviceTokenRepository;
import com.byarrival.domain.notification.repository.NotificationRepository;
import com.byarrival.domain.notification.service.NotificationSendService;
import com.byarrival.domain.notification.service.NotificationService;
import com.byarrival.domain.user.entity.SocialProvider;
import com.byarrival.domain.user.entity.User;
import com.byarrival.domain.user.repository.UserRepository;
import com.byarrival.support.TestcontainersConfig;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 실제 DB와 보안 필터로 기기 등록, 권한·수신 설정, 테스트 발송, 알림함, 로그아웃 시 토큰 삭제를 확인한다.
 * 푸시는 FCM 대신 로그 발송기(LoggingPushSender)로 보낸다. 로컬 설정에서 FCM을 켜 두어도 실제로 발송하지 않도록 끈다.
 */
@SpringBootTest(properties = "notification.push.fcm-enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class NotificationFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DeviceTokenRepository deviceTokenRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationSendService notificationSendService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long userId;
    private TokenResponse tokens;
    private String fcmToken;

    @BeforeEach
    void setUp() {
        userId = newUser();
        tokens = tokenService.issue(userId);
        fcmToken = "fcm-" + UUID.randomUUID();
    }

    @Test
    void 기기_토큰을_여러_번_등록해도_한_행만_남고_다른_계정이_등록하면_그_계정으로_옮겨진다() throws Exception {
        registerDevice(tokens, fcmToken);
        registerDevice(tokens, fcmToken);
        assertThat(deviceTokenRepository.findByUserId(userId)).extracting(DeviceToken::getToken)
            .containsExactly(fcmToken);

        Long otherUserId = newUser();
        registerDevice(tokenService.issue(otherUserId), fcmToken);

        assertThat(deviceTokenRepository.findByUserId(userId)).isEmpty();
        assertThat(deviceTokenRepository.findByUserId(otherUserId)).hasSize(1);
    }

    @Test
    void 기기_토큰은_내_것만_지울_수_있다() throws Exception {
        registerDevice(tokens, fcmToken);
        TokenResponse otherUser = tokenService.issue(newUser());

        perform(otherUser, delete("/api/v1/devices"), "{\"fcmToken\":\"%s\"}".formatted(fcmToken))
            .andExpect(status().isOk());
        assertThat(deviceTokenRepository.existsByUserId(userId)).isTrue();

        perform(tokens, delete("/api/v1/devices"), "{\"fcmToken\":\"%s\"}".formatted(fcmToken))
            .andExpect(status().isOk());
        assertThat(deviceTokenRepository.existsByUserId(userId)).isFalse();
    }

    @Test
    void 플랫폼_없이_기기를_등록하면_INVALID_INPUT_VALUE로_응답한다() throws Exception {
        perform(tokens, put("/api/v1/devices"), "{\"fcmToken\":\"%s\"}".formatted(fcmToken))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void 토큰_없이_알림_API를_호출하면_UNAUTHORIZED로_응답한다() throws Exception {
        mockMvc.perform(get("/api/v1/notifications"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void 수신_설정은_처음에_모두_켜져_있고_바꾼_카테고리만_반영된다() throws Exception {
        perform(tokens, put("/api/v1/users/me/notification-permission"), "{\"permission\":\"DENIED\"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.permission").value("DENIED"));

        perform(tokens, get("/api/v1/notification-settings"), null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.permission").value("DENIED"))
            .andExpect(jsonPath("$.data.settings.length()").value(1))
            .andExpect(jsonPath("$.data.settings[0].category").value("ACTIVITY"))
            .andExpect(jsonPath("$.data.settings[0].enabled").value(true));

        String body = "{\"settings\":[{\"category\":\"ACTIVITY\",\"enabled\":true},"
            + "{\"category\":\"ACTIVITY\",\"enabled\":false}]}";
        perform(tokens, patch("/api/v1/notification-settings"), body)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.settings[0].enabled").value(false));

        perform(tokens, patch("/api/v1/notification-settings"),
            "{\"settings\":[{\"category\":\"ACTIVITY\",\"enabled\":true}]}")
            .andExpect(jsonPath("$.data.settings[0].enabled").value(true));
    }

    @Test
    void 행동_유도_알림은_유형별_하루_1번_합쳐서_하루_2번까지만_저장된다() {
        assertThat(notificationSendService.send(action(NotificationType.ROUTE_READY))).isPresent();
        assertThat(notificationSendService.send(action(NotificationType.ROUTE_READY))).isEmpty();
        assertThat(notificationSendService.send(action(NotificationType.STREAK_BREAK))).isPresent();
        assertThat(notificationSendService.send(action(NotificationType.ROUTE_INCOMPLETE))).isEmpty();
        assertThat(notificationSendService.send(notice(userId))).isPresent();
    }

    @Test
    void 수신_설정을_끄면_운영_안내까지_모든_알림이_알림함에만_저장된다() throws Exception {
        registerDevice(tokens, fcmToken);
        perform(tokens, patch("/api/v1/notification-settings"),
            "{\"settings\":[{\"category\":\"ACTIVITY\",\"enabled\":false}]}")
            .andExpect(status().isOk());

        Long actionId = notificationSendService.send(action(NotificationType.ROUTE_READY)).orElseThrow();
        Long noticeId = notificationSendService.send(notice(userId)).orElseThrow();

        assertThat(notificationRepository.findById(actionId).orElseThrow().getPushStatus())
            .isEqualTo(PushStatus.SKIPPED);
        assertThat(notificationRepository.findById(noticeId).orElseThrow().getPushStatus())
            .isEqualTo(PushStatus.SKIPPED);
        perform(tokens, get("/api/v1/notifications/unread-count"), null)
            .andExpect(jsonPath("$.data.count").value(2));
    }

    @Test
    void 없는_카테고리나_빈_목록으로_설정을_바꾸면_400으로_응답한다() throws Exception {
        perform(tokens, patch("/api/v1/notification-settings"),
            "{\"settings\":[{\"category\":\"MORNING\",\"enabled\":false}]}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"));
        perform(tokens, patch("/api/v1/notification-settings"), "{\"settings\":[]}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void 기기가_없으면_테스트_발송은_NO_REGISTERED_DEVICE로_응답한다() throws Exception {
        perform(tokens, post("/api/v1/notifications/test"), null)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("NO_REGISTERED_DEVICE"));
    }

    @Test
    void 테스트_발송하면_내_기기로_푸시되고_알림함에서_읽을_수_있다() throws Exception {
        registerDevice(tokens, fcmToken);

        perform(tokens, post("/api/v1/notifications/test"), null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.sentDeviceCount").value(1));

        perform(tokens, get("/api/v1/notifications/unread-count"), null)
            .andExpect(jsonPath("$.data.count").value(1));
        perform(tokens, get("/api/v1/notifications"), null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.notifications[0].type").value("TEST"))
            .andExpect(jsonPath("$.data.notifications[0].read").value(false))
            .andExpect(jsonPath("$.data.hasNext").value(false));

        Notification sent = notificationRepository.findAll().stream()
            .filter(notification -> notification.getUserId().equals(userId))
            .findFirst().orElseThrow();
        assertThat(sent.getPushStatus()).isEqualTo(PushStatus.SENT);

        perform(tokens, patch("/api/v1/notifications/" + sent.getId() + "/read"), null)
            .andExpect(status().isOk());
        perform(tokens, get("/api/v1/notifications/unread-count"), null)
            .andExpect(jsonPath("$.data.count").value(0));
    }

    @Test
    void 다른_사람의_알림을_읽음_처리하면_NOTIFICATION_NOT_FOUND로_응답한다() throws Exception {
        Long othersNotification = notificationSendService.send(notice(newUser())).orElseThrow();

        perform(tokens, patch("/api/v1/notifications/" + othersNotification + "/read"), null)
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));
    }

    @Test
    void 알림함은_커서로_나눠_받고_모두_읽음_처리할_수_있다() throws Exception {
        for (int i = 0; i < 3; i++) {
            notificationSendService.send(notice(userId));
        }

        String first = perform(tokens, get("/api/v1/notifications?size=2"), null)
            .andExpect(jsonPath("$.data.notifications.length()").value(2))
            .andExpect(jsonPath("$.data.hasNext").value(true))
            .andReturn().getResponse().getContentAsString();
        String cursor = first.replaceAll(".*\"nextCursor\":(\\d+).*", "$1");
        perform(tokens, get("/api/v1/notifications?size=2&cursor=" + cursor), null)
            .andExpect(jsonPath("$.data.notifications.length()").value(1))
            .andExpect(jsonPath("$.data.hasNext").value(false));

        perform(tokens, patch("/api/v1/notifications/read-all"), null).andExpect(status().isOk());
        perform(tokens, get("/api/v1/notifications/unread-count"), null)
            .andExpect(jsonPath("$.data.count").value(0));
    }

    @Test
    void 운영_안내는_커밋된_뒤_비동기로_푸시된다() throws Exception {
        registerDevice(tokens, fcmToken);

        Long id = notificationSendService.send(notice(userId)).orElseThrow();

        PushStatus status = PushStatus.PENDING;
        for (int i = 0; i < 50 && status == PushStatus.PENDING; i++) {
            Thread.sleep(100);
            status = notificationRepository.findById(id).orElseThrow().getPushStatus();
        }
        assertThat(status).isEqualTo(PushStatus.SENT);
    }

    @Test
    void 삼십일이_지난_알림은_보이지_않고_정리_작업에서_지워진다() throws Exception {
        Long id = notificationSendService.send(notice(userId)).orElseThrow();
        jdbcTemplate.update("UPDATE notification SET created_at = ? WHERE id = ?",
            LocalDateTime.now().minusDays(31), id);

        perform(tokens, get("/api/v1/notifications"), null)
            .andExpect(jsonPath("$.data.notifications.length()").value(0));
        perform(tokens, get("/api/v1/notifications/unread-count"), null)
            .andExpect(jsonPath("$.data.count").value(0));

        notificationService.deleteExpired();
        assertThat(notificationRepository.findById(id)).isEmpty();
    }

    @Test
    void 행동_유도_알림은_같은_서비스일에_같은_유형을_두_번_저장할_수_없다() {
        LocalDate today = LocalDate.of(2026, 11, 8);
        notificationRepository.saveAndFlush(routeReady(today));

        assertThatThrownBy(() -> notificationRepository.saveAndFlush(routeReady(today)))
            .isInstanceOf(DataIntegrityViolationException.class);
        notificationRepository.saveAndFlush(routeReady(today.plusDays(1)));
        assertThat(notificationRepository.countByUserIdAndServiceDateAndTypeIn(
            userId, today, NotificationType.actionTypes())).isEqualTo(1);
    }

    @Test
    void 로그아웃할_때_FCM_토큰을_보내면_그_기기로_더_이상_푸시하지_않는다() throws Exception {
        registerDevice(tokens, fcmToken);

        perform(tokens, post("/api/v1/auth/logout"),
            "{\"refreshToken\":\"%s\",\"fcmToken\":\"%s\"}".formatted(tokens.refreshToken(), fcmToken))
            .andExpect(status().isOk());

        assertThat(deviceTokenRepository.existsByUserId(userId)).isFalse();
    }

    private Long newUser() {
        return userRepository.save(User.signUp(SocialProvider.KAKAO, UUID.randomUUID().toString())).getId();
    }

    private void registerDevice(TokenResponse owner, String token) throws Exception {
        perform(owner, put("/api/v1/devices"), "{\"fcmToken\":\"%s\",\"platform\":\"ANDROID\"}".formatted(token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    private org.springframework.test.web.servlet.ResultActions perform(
        TokenResponse owner, MockHttpServletRequestBuilder request, String body) throws Exception {
        request.header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.accessToken());
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request);
    }

    private NotificationRequest notice(Long targetUserId) {
        return NotificationRequest.of(targetUserId, NotificationType.SERVICE_NOTICE, "점검 안내", "02:00~03:00 점검");
    }

    private NotificationRequest action(NotificationType type) {
        return NotificationRequest.of(userId, type, "제목", "내용");
    }

    private Notification routeReady(LocalDate serviceDate) {
        return Notification.create(userId, NotificationType.ROUTE_READY, "노선 준비", "내용", Map.of(), serviceDate,
            false);
    }
}
