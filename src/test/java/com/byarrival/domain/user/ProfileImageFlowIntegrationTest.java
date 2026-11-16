package com.byarrival.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.common.exception.CommonErrorCode;
import com.byarrival.common.storage.FileStorage;
import com.byarrival.domain.auth.service.TokenService;
import com.byarrival.domain.user.entity.SocialProvider;
import com.byarrival.domain.user.entity.User;
import com.byarrival.domain.user.repository.UserRepository;
import com.byarrival.domain.user.scheduler.WithdrawnUserPurgeScheduler;
import com.byarrival.support.TestcontainersConfig;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 실제 DB와 보안 필터로 프로필 이미지 변경·삭제·파기를 확인한다. S3는 가짜로 바꾼다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class ProfileImageFlowIntegrationTest {

    private static final String PROFILE_IMAGE = "/api/v1/users/me/profile-image";
    private static final String BASE_URL = "https://cdn.test/local/";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private WithdrawnUserPurgeScheduler purgeScheduler;

    @MockitoBean
    private FileStorage fileStorage;

    private Long userId;
    private String accessToken;

    @BeforeEach
    void setUp() {
        userId = userRepository.save(User.signUp(SocialProvider.KAKAO, UUID.randomUUID().toString())).getId();
        accessToken = tokenService.issue(userId).accessToken();
        given(fileStorage.upload(anyString(), any(), anyString()))
            .willAnswer(invocation -> BASE_URL + invocation.getArgument(0));
    }

    @Test
    void 이미지를_올리면_JPG로_저장하고_내_정보의_프로필_URL을_바꾼다() throws Exception {
        upload(png())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.profileImageUrl").isString());

        assertThat(profileImageUrl()).startsWith(BASE_URL + "profile/" + userId + "/").endsWith(".jpg");
        verify(fileStorage).upload(startsWith("profile/" + userId + "/"), any(), eq("image/jpeg"));
    }

    @Test
    void 이미지를_바꾸면_이전_파일을_지운다() throws Exception {
        upload(png()).andExpect(status().isOk());
        String first = profileImageUrl();

        upload(png()).andExpect(status().isOk());

        assertThat(profileImageUrl()).isNotEqualTo(first);
        verify(fileStorage).delete(first);
    }

    @Test
    void 삭제하면_기본_이미지_null로_되돌리고_파일을_지운다() throws Exception {
        upload(png()).andExpect(status().isOk());
        String uploaded = profileImageUrl();

        mockMvc.perform(delete(PROFILE_IMAGE).header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.profileImageUrl").doesNotExist());

        verify(fileStorage).delete(uploaded);
    }

    @Test
    void 이미지가_아니면_INVALID_PROFILE_IMAGE이고_저장하지_않는다() throws Exception {
        upload("not image".getBytes())
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_PROFILE_IMAGE"));

        verify(fileStorage, never()).upload(anyString(), any(), anyString());
        assertThat(profileImageUrl()).isNull();
    }

    @Test
    void image_파트가_없으면_400이다() throws Exception {
        mockMvc.perform(multipart(HttpMethod.PUT, PROFILE_IMAGE)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("MISSING_REQUEST_PARAMETER"));
    }

    @Test
    void 저장소에_올리지_못하면_503이고_프로필은_그대로다() throws Exception {
        willThrow(new BusinessException(CommonErrorCode.STORAGE_UNAVAILABLE))
            .given(fileStorage).upload(anyString(), any(), anyString());

        upload(png())
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("STORAGE_UNAVAILABLE"));

        assertThat(profileImageUrl()).isNull();
    }

    @Test
    void 보관_기간이_지난_탈퇴_회원을_파기하면_프로필_이미지_파일도_지운다() {
        String url = BASE_URL + "profile/" + userId + "/old.jpg";
        jdbcTemplate.update("UPDATE users SET status = 'WITHDRAWN', withdrawn_at = ?, profile_image_url = ? WHERE id = ?",
            LocalDateTime.now().minusMonths(6).minusDays(1), url, userId);

        purgeScheduler.purge();

        assertThat(userRepository.findById(userId)).isEmpty();
        verify(fileStorage).delete(url);
    }

    @Test
    void 토큰_없이_올리면_401이다() throws Exception {
        mockMvc.perform(multipart(HttpMethod.PUT, PROFILE_IMAGE).file(new MockMultipartFile("image", png())))
            .andExpect(status().isUnauthorized());
    }

    private ResultActions upload(byte[] content) throws Exception {
        return mockMvc.perform(multipart(HttpMethod.PUT, PROFILE_IMAGE)
            .file(new MockMultipartFile("image", "profile.png", "image/png", content))
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));
    }

    private String profileImageUrl() {
        return jdbcTemplate.queryForObject("SELECT profile_image_url FROM users WHERE id = ?", String.class, userId);
    }

    private static byte[] png() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB), "png", bytes);
        return bytes.toByteArray();
    }
}
