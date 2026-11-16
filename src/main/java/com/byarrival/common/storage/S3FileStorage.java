package com.byarrival.common.storage;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.common.exception.CommonErrorCode;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * S3에 저장하고 CloudFront 주소로 돌려준다. 버킷은 비공개이고 CloudFront(OAC)만 읽을 수 있다.
 * 키 설정이 없으면 업로드는 STORAGE_UNAVAILABLE, 삭제는 건너뛴다.
 */
@Slf4j
@Component
public class S3FileStorage implements FileStorage {

    private final StorageProperties properties;
    private volatile S3Client client;

    public S3FileStorage(StorageProperties properties) {
        this.properties = properties;
    }

    @Override
    public String upload(String path, byte[] content, String contentType) {
        if (!properties.isConfigured()) {
            throw new BusinessException(CommonErrorCode.STORAGE_UNAVAILABLE);
        }
        String key = properties.keyPrefix() + "/" + path;
        try {
            client().putObject(PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(key)
                .contentType(contentType)
                .build(), RequestBody.fromBytes(content));
        } catch (SdkException e) {
            log.error("[Storage] S3 업로드 실패: {}", e.getClass().getSimpleName(), e);
            throw new BusinessException(CommonErrorCode.STORAGE_UNAVAILABLE);
        }
        return properties.publicBaseUrl() + "/" + key;
    }

    @Override
    public void delete(String url) {
        if (!properties.isConfigured()) {
            return;
        }
        keyOf(url).ifPresent(key -> client().deleteObject(DeleteObjectRequest.builder()
            .bucket(properties.bucket())
            .key(key)
            .build()));
    }

    @Override
    public Optional<String> keyOf(String url) {
        String base = properties.publicBaseUrl() + "/" + properties.keyPrefix() + "/";
        if (url == null || !url.startsWith(base)) {
            return Optional.empty();
        }
        return Optional.of(url.substring(properties.publicBaseUrl().length() + 1));
    }

    private S3Client client() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    client = S3Client.builder()
                        .region(Region.of(properties.region()))
                        .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(properties.accessKey(), properties.secretKey())))
                        .build();
                }
            }
        }
        return client;
    }
}
