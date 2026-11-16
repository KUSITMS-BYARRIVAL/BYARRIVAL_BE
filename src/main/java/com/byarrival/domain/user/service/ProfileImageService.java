package com.byarrival.domain.user.service;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.common.exception.CommonErrorCode;
import com.byarrival.common.storage.FileDeletionService;
import com.byarrival.common.storage.FileStorage;
import com.byarrival.domain.user.dto.response.UserMeResponse;
import com.byarrival.domain.user.entity.User;
import com.byarrival.domain.user.exception.UserErrorCode;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * 프로필 이미지 변경·삭제(MY-02). 이미지 변환과 S3 저장은 외부 작업이라 트랜잭션 밖에서 하고, 회원의 URL만 트랜잭션으로 바꾼다.
 * 이전 이미지는 URL을 바꾸는 트랜잭션에서 삭제 작업으로 남기고, 파일 삭제 작업자가 지운다(늦어도 10분 안).
 */
@Slf4j
@Service
public class ProfileImageService {

    private static final String CONTENT_TYPE = "image/jpeg";

    private final UserService userService;
    private final ProfileImageProcessor profileImageProcessor;
    private final FileStorage fileStorage;
    private final FileDeletionService fileDeletionService;
    private final TransactionTemplate transactionTemplate;

    public ProfileImageService(UserService userService, ProfileImageProcessor profileImageProcessor,
        FileStorage fileStorage, FileDeletionService fileDeletionService,
        PlatformTransactionManager transactionManager) {
        this.userService = userService;
        this.profileImageProcessor = profileImageProcessor;
        this.fileStorage = fileStorage;
        this.fileDeletionService = fileDeletionService;
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
        try {
            transactionTemplate.executeWithoutResult(status -> replace(userId, url));
        } catch (RuntimeException e) {
            // 회원 URL을 바꾸지 못했으면 방금 올린 파일은 쓰이지 않는다
            discard(url);
            throw e;
        }
        return userService.getMe(userId);
    }

    /**
     * 기본 이미지로 되돌린다. 앱은 profileImageUrl이 null이면 기본 이미지를 보여 준다.
     */
    public UserMeResponse delete(Long userId) {
        transactionTemplate.executeWithoutResult(status -> replace(userId, null));
        return userService.getMe(userId);
    }

    // 회원 URL을 바꾸고, 같은 트랜잭션에서 이전 파일을 삭제 작업으로 남긴다
    private void replace(Long userId, String url) {
        User user = userService.getActiveUserForUpdate(userId);
        String previous = user.getProfileImageUrl();
        user.changeProfileImage(url);
        if (previous != null) {
            fileDeletionService.enqueue(List.of(previous));
        }
    }

    // 쓰이지 않게 된 새 파일을 지운다. 작업으로도 남기지 못하면 로그만 남긴다
    private void discard(String url) {
        try {
            fileDeletionService.enqueueNow(url);
        } catch (RuntimeException e) {
            log.warn("[ProfileImage] 쓰이지 않는 이미지 삭제 작업을 남기지 못함: {} - {}", url, e.getClass().getSimpleName());
        }
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
