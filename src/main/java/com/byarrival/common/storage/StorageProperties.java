package com.byarrival.common.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * S3 파일 저장 설정. 키가 비어 있으면 저장하지 않는다(테스트).
 *
 * @param keyPrefix     환경별 폴더(local, prod). 같은 버킷을 나눠 쓴다
 * @param publicBaseUrl 저장한 파일을 읽는 CloudFront 주소. 끝에 /를 붙이지 않는다
 */
@ConfigurationProperties(prefix = "storage.s3")
public record StorageProperties(
    String bucket,
    String region,
    String keyPrefix,
    String publicBaseUrl,
    String accessKey,
    String secretKey
) {

    public boolean isConfigured() {
        return StringUtils.hasText(accessKey) && StringUtils.hasText(secretKey) && StringUtils.hasText(bucket);
    }
}
