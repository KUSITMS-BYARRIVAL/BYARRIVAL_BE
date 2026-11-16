package com.byarrival.common.storage;

import java.util.Optional;

/**
 * 공개 URL로 읽을 수 있는 파일 저장소. 외부 호출이라 트랜잭션 밖에서 부른다.
 */
public interface FileStorage {

    /**
     * @param path 환경 폴더 아래 경로. 예: profile/1/uuid.jpg
     * @return 저장한 파일의 공개 URL
     */
    String upload(String path, byte[] content, String contentType);

    /**
     * 저장소 설정(키)이 있어 실제로 저장·삭제할 수 있는지
     */
    boolean isAvailable();

    /**
     * 이 저장소가 만든 URL만 지운다. 다른 주소면 아무것도 하지 않는다.
     */
    void delete(String url);

    /**
     * 이 저장소가 만든 URL이면 그 파일의 키
     */
    Optional<String> keyOf(String url);
}
