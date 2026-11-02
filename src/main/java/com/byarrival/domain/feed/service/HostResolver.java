package com.byarrival.domain.feed.service;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * 호스트 이름을 IP 주소로 해석한다. 테스트에서 DNS 조회를 대체할 수 있게 분리했다.
 */
@FunctionalInterface
public interface HostResolver {

    InetAddress[] resolve(String host) throws UnknownHostException;
}
