package com.byarrival.domain.auth.client;

import com.byarrival.domain.user.entity.SocialProvider;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Provider별 SocialAuthClient 모음
 */
@Component
public class SocialAuthClients {

    private final Map<SocialProvider, SocialAuthClient> clients = new EnumMap<>(SocialProvider.class);

    public SocialAuthClients(List<SocialAuthClient> clients) {
        clients.forEach(client -> this.clients.put(client.provider(), client));
        for (SocialProvider provider : SocialProvider.values()) {
            if (!this.clients.containsKey(provider)) {
                throw new IllegalStateException(provider + " SocialAuthClient 구현이 없습니다.");
            }
        }
    }

    public SocialAuthClient get(SocialProvider provider) {
        return clients.get(provider);
    }
}
