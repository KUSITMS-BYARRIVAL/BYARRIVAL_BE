package com.byarrival.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 외부 서비스 토큰을 DB에 저장할 때 쓰는 암호화 키.
 *
 * @param key AES 키(16·24·32바이트)를 base64로 인코딩한 값. 비어 있으면 암호화가 필요한 토큰을 저장하지 않는다(로컬·테스트).
 */
@ConfigurationProperties(prefix = "token-encryption")
public record TokenEncryptionProperties(String key) {
}
