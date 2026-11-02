package com.byarrival.domain.feed.service;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.domain.feed.exception.FeedErrorCode;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Feed URL이 외부 공개 주소인지 검사한다. 내부망 주소로 서버가 요청을 보내지 않도록 막는다.
 */
@Component
public class FeedUrlValidator {

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");
    private static final int MAX_URL_LENGTH = 500;

    private final HostResolver hostResolver;

    @Autowired
    public FeedUrlValidator() {
        this(InetAddress::getAllByName);
    }

    FeedUrlValidator(HostResolver hostResolver) {
        this.hostResolver = hostResolver;
    }

    public URI validate(String url) {
        URI uri = parse(url);
        for (InetAddress address : resolve(uri.getHost())) {
            if (isBlocked(address)) {
                throw invalid("내부망 주소로 해석되는 Feed URL입니다.");
            }
        }
        return uri;
    }

    private URI parse(String url) {
        if (url == null || url.isBlank() || url.length() > MAX_URL_LENGTH) {
            throw invalid("Feed URL은 1자 이상 " + MAX_URL_LENGTH + "자 이하여야 합니다.");
        }
        URI uri;
        try {
            uri = new URI(url.strip());
        } catch (URISyntaxException e) {
            throw invalid("Feed URL 형식이 올바르지 않습니다.");
        }
        String scheme = uri.getScheme();
        if (scheme == null || !ALLOWED_SCHEMES.contains(scheme.toLowerCase(Locale.ROOT))) {
            throw invalid("http 또는 https Feed URL만 사용할 수 있습니다.");
        }
        if (uri.getHost() == null || uri.getRawUserInfo() != null) {
            throw invalid("Feed URL 형식이 올바르지 않습니다.");
        }
        return uri;
    }

    private InetAddress[] resolve(String host) {
        try {
            return hostResolver.resolve(host);
        } catch (UnknownHostException e) {
            throw invalid("Feed URL의 호스트를 찾을 수 없습니다.");
        }
    }

    private boolean isBlocked(InetAddress address) {
        // 169.254.169.254(클라우드 메타데이터)는 link-local에 포함된다
        return address.isLoopbackAddress()
            || address.isAnyLocalAddress()
            || address.isLinkLocalAddress()
            || address.isSiteLocalAddress()
            || address.isMulticastAddress()
            || isReservedIpv4(address)
            || isIpv6UniqueLocal(address);
    }

    private boolean isReservedIpv4(InetAddress address) {
        if (!(address instanceof Inet4Address)) {
            return false;
        }
        byte[] ip = address.getAddress();
        int first = ip[0] & 0xFF;
        int second = ip[1] & 0xFF;
        int third = ip[2] & 0xFF;
        return (first == 100 && second >= 64 && second <= 127)   // 100.64.0.0/10 CGNAT
            || (first == 198 && (second == 18 || second == 19))  // 198.18.0.0/15 벤치마크
            || (first == 192 && second == 0 && third == 0)       // 192.0.0.0/24 IETF 프로토콜
            || first >= 240;                                     // 240.0.0.0/4 예약, 브로드캐스트
    }

    private boolean isIpv6UniqueLocal(InetAddress address) {
        // fc00::/7 는 isSiteLocalAddress()가 잡지 않는다
        return address instanceof Inet6Address && (address.getAddress()[0] & 0xFE) == 0xFC;
    }

    private BusinessException invalid(String message) {
        return new BusinessException(FeedErrorCode.INVALID_FEED_URL, message);
    }
}
