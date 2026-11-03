package com.byarrival.domain.user.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.byarrival.common.config.JpaAuditingConfig;
import com.byarrival.domain.user.entity.SocialProvider;
import com.byarrival.domain.user.entity.User;
import com.byarrival.domain.user.entity.UserStatus;
import com.byarrival.support.TestcontainersConfig;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfig.class, JpaAuditingConfig.class, UserRepositoryTest.FixedClockConfig.class})
class UserRepositoryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 30, 9, 0);

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfig {

        @Bean
        Clock clock() {
            ZoneId kst = ZoneId.of("Asia/Seoul");
            return Clock.fixed(ZonedDateTime.of(NOW, kst).toInstant(), kst);
        }
    }

    @Autowired
    private UserRepository userRepository;

    @Test
    void 회원을_저장하면_생성_수정_시각이_KST_Clock_기준으로_채워진다() {
        User user = userRepository.saveAndFlush(User.signUp(SocialProvider.KAKAO, "12345"));

        assertThat(user.getCreatedAt()).isEqualTo(NOW);
        assertThat(user.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void 소셜_계정과_상태로_활성_회원을_조회한다() {
        userRepository.saveAndFlush(User.signUp(SocialProvider.APPLE, "apple-sub"));

        assertThat(userRepository.findByProviderAndProviderUserIdAndStatus(
            SocialProvider.APPLE, "apple-sub", UserStatus.ACTIVE)).isPresent();
        assertThat(userRepository.findByProviderAndProviderUserIdAndStatus(
            SocialProvider.KAKAO, "apple-sub", UserStatus.ACTIVE)).isEmpty();
    }

    @Test
    void 같은_소셜_계정의_활성_회원은_중복_저장할_수_없다() {
        userRepository.saveAndFlush(User.signUp(SocialProvider.KAKAO, "12345"));

        assertThatThrownBy(() -> userRepository.saveAndFlush(User.signUp(SocialProvider.KAKAO, "12345")))
            .isInstanceOf(DataIntegrityViolationException.class);
    }
}
