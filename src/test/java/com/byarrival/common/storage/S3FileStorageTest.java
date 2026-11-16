package com.byarrival.common.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.common.exception.CommonErrorCode;
import org.junit.jupiter.api.Test;

class S3FileStorageTest {

    private static final String BASE_URL = "https://d12zg2i2wojklm.cloudfront.net";

    @Test
    void 키_설정이_없으면_업로드는_STORAGE_UNAVAILABLE이고_삭제는_건너뛴다() {
        S3FileStorage storage = new S3FileStorage(properties("", ""));

        assertThatThrownBy(() -> storage.upload("profile/1/a.jpg", new byte[] {1}, "image/jpeg"))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.STORAGE_UNAVAILABLE));
        storage.delete(BASE_URL + "/local/profile/1/a.jpg");
    }

    @Test
    void 이_저장소의_환경_폴더_URL에서만_키를_꺼낸다() {
        S3FileStorage storage = new S3FileStorage(properties("access", "secret"));

        assertThat(storage.keyOf(BASE_URL + "/local/profile/1/a.jpg")).contains("local/profile/1/a.jpg");
        // 다른 환경(prod) 파일이나 외부 주소는 지우지 않는다
        assertThat(storage.keyOf(BASE_URL + "/prod/profile/1/a.jpg")).isEmpty();
        assertThat(storage.keyOf("https://example.com/local/profile/1/a.jpg")).isEmpty();
        assertThat(storage.keyOf(null)).isEmpty();
    }

    private static StorageProperties properties(String accessKey, String secretKey) {
        return new StorageProperties("byarrival-media", "ap-northeast-2", "local", BASE_URL, accessKey, secretKey);
    }
}
