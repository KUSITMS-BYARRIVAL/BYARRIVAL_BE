package com.byarrival.domain.user.service;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.common.exception.CommonErrorCode;
import com.byarrival.common.storage.FileStorage;
import com.byarrival.domain.user.dto.response.UserMeResponse;
import com.byarrival.domain.user.entity.User;
import com.byarrival.domain.user.exception.UserErrorCode;
import java.io.IOException;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * 프로필 이미지 변경·삭제(MY-02). 이미지 변환과 S3 저장은 외부 작업이라 트랜잭션 밖에서 하고, 회원의 URL만 트랜잭션으로 바꾼다.
 * 이전 이미지는 URL을 바꾼 뒤에 지운다. 지우지 못해도 프로필 변경은 그대로 두고 로그만 남긴다.
 */
@Slf4j
@Service
public class ProfileImageService {

    private static final String CONTENT_TYPE = "image/jpeg";

    private final UserService userService;
    private final ProfileImageProcessor profileImageProcessor;
    private final FileStorage fileStorage;
    private final TransactionTemplate transactionTemplate;

    public ProfileImageService(UserService userService, ProfileImageProcessor profileImageProcessor,
        FileStorage fileStorage, PlatformTransactionManager transactionManager) {
        this.userService = userService;
        this.profileImageProcessor = profileImageProcessor;
        this.fileStorage = fileStorage;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * 탈퇴했거나 없는 회원이면 UNAUTHORIZED로 응답한다.
     */
    public UserMeResponse change(Long userId, MultipartFile image) {
        if (!userService.isActiveUser(userId)) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
        byte[] jpeg = profileImageProcessor.toProfileJpeg(read(image));
        String url = fileStorage.upload("profile/" + userId + "/" + UUID.randomUUID() + ".jpg", jpeg, CONTENT_TYPE);
        String previous;
        try {
            previous = transactionTemplate.execute(status -> replace(userId, url));
        } catch (RuntimeException e) {
            // 회원 URL을 바꾸지 못했으면 방금 올린 파일은 쓰이지 않는다
            deleteQuietly(url);
            throw e;
        }
        deleteQuietly(previous);
        return userService.getMe(userId);
    }

    /**
     * 기본 이미지로 되돌린다. 앱은 profileImageUrl이 null이면 기본 이미지를 보여 준다.
     */
    public UserMeResponse delete(Long userId) {
        String previous = transactionTemplate.execute(status -> replace(userId, null));
        deleteQuietly(previous);
        return userService.getMe(userId);
    }

    /**
     * 더 이상 쓰지 않는 이미지 파일을 지운다. 실패해도 예외를 던지지 않는다.
     */
    public void deleteQuietly(String url) {
        if (url == null) {
            return;
        }
        try {
            fileStorage.delete(url);
        } catch (RuntimeException e) {
            log.warn("[ProfileImage] 이미지 파일 삭제 실패: {} - {}", url, e.getClass().getSimpleName());
        }
    }

    // 바꾸기 전 URL을 돌려준다
    private String replace(Long userId, String url) {
        User user = userService.getActiveUserForUpdate(userId);
        String previous = user.getProfileImageUrl();
        user.changeProfileImage(url);
        return previous;
    }

    private static byte[] read(MultipartFile image) {
        if (image.isEmpty()) {
            throw new BusinessException(UserErrorCode.INVALID_PROFILE_IMAGE);
        }
        try {
            return image.getBytes();
        } catch (IOException e) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST_BODY);
        }
    }
}
