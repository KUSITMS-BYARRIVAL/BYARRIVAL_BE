package com.byarrival.domain.notification.push;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 설정에 따라 FCM 발송기나 로그 발송기를 만든다. FCM을 켰는데 서비스 계정 키가 없으면 앱이 뜨지 않게 한다.
 */
@Configuration
@EnableConfigurationProperties(PushProperties.class)
public class PushSenderConfig {

    private static final String FIREBASE_APP_NAME = "byarrival";

    @Bean
    public PushSender pushSender(PushProperties properties) {
        if (!properties.fcmEnabled()) {
            return new LoggingPushSender();
        }
        FirebaseApp app = FirebaseApp.getApps().stream()
            .filter(existing -> existing.getName().equals(FIREBASE_APP_NAME))
            .findFirst()
            .orElseGet(() -> FirebaseApp.initializeApp(firebaseOptions(properties), FIREBASE_APP_NAME));
        return new FcmPushSender(FirebaseMessaging.getInstance(app), properties.androidChannelId());
    }

    private FirebaseOptions firebaseOptions(PushProperties properties) {
        String encoded = properties.fcmCredentialsBase64();
        if (encoded == null || encoded.isBlank()) {
            throw new IllegalStateException("FCM을 켜려면 notification.push.fcm-credentials-base64가 필요합니다.");
        }
        try {
            byte[] json = Base64.getDecoder().decode(encoded.strip());
            GoogleCredentials credentials = GoogleCredentials.fromStream(new ByteArrayInputStream(json));
            return FirebaseOptions.builder().setCredentials(credentials).build();
        } catch (IllegalArgumentException | IOException e) {
            throw new IllegalStateException("FCM 서비스 계정 키를 읽을 수 없습니다.", e);
        }
    }
}
