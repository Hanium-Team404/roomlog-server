package com.roomlog.user.service;

import com.roomlog.global.exception.CustomException;
import com.roomlog.global.exception.ErrorCode;
import com.roomlog.global.infra.AiClient;
import com.roomlog.global.infra.R2FileUploader;
import com.roomlog.user.domain.User;
import com.roomlog.user.dto.DeleteUserResponse;
import com.roomlog.user.dto.GetMyProfileResponse;
import com.roomlog.user.dto.UpdateUserRequest;
import com.roomlog.user.dto.UpdateUserResponse;
import com.roomlog.user.repository.UserPurgeRepository;
import com.roomlog.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserPurgeRepository userPurgeRepository;
    private final R2FileUploader r2FileUploader;
    private final AiClient aiClient;

    @Transactional(readOnly = true)
    public GetMyProfileResponse getMyProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.COMMON_401));
        return GetMyProfileResponse.from(user);
    }

    @Transactional
    public UpdateUserResponse updateUser(Long userId, UpdateUserRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.COMMON_401));
        user.updateNickname(request.getNickname());
        return UpdateUserResponse.from(user);
    }

    /**
     * 회원 탈퇴. 개인정보 처리방침(탈퇴 후 30일 이내 파기)에 따라 즉시 실제 삭제한다.
     * 1) R2·AI 서버 S3의 파일을 지우고 2) DB 행을 모두 지운다.
     * 파일 삭제는 외부 서버 장애로 실패해도 탈퇴를 막지 않고 로그만 남긴다(수동 정리용).
     * 이 메서드는 외부 호출이 길어질 수 있어 트랜잭션으로 묶지 않고, DB 삭제만 별도 트랜잭션으로 처리한다.
     */
    public DeleteUserResponse deleteUser(Long userId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.COMMON_401));

        List<Long> scanIds = userPurgeRepository.findScanIds(userId);
        List<String> defectImageUrls = userPurgeRepository.findDefectImageUrls(userId);

        deleteStoredFiles(userId, scanIds, defectImageUrls);
        userPurgeRepository.deleteAllUserData(userId);

        log.info("회원 탈퇴 완료 - userId: {}, scans: {}, defectImages: {}", userId, scanIds.size(), defectImageUrls.size());
        return DeleteUserResponse.of(userId);
    }

    private void deleteStoredFiles(Long userId, List<Long> scanIds, List<String> defectImageUrls) {
        for (Long scanId : scanIds) {
            try {
                r2FileUploader.deleteByPrefix("scans/" + scanId + "/");
            } catch (Exception e) {
                log.error("R2 스캔 파일 삭제 실패 - userId: {}, scanId: {}, error: {}", userId, scanId, e.getMessage());
            }
            try {
                aiClient.deleteScanFiles(scanId);
            } catch (Exception e) {
                log.error("AI 서버 스캔 파일 삭제 실패 - userId: {}, scanId: {}, error: {}", userId, scanId, e.getMessage());
            }
        }

        try {
            aiClient.deleteDefectImages(defectImageUrls);
        } catch (Exception e) {
            log.error("AI 서버 하자 이미지 삭제 실패 - userId: {}, urls: {}, error: {}", userId, defectImageUrls, e.getMessage());
        }
    }
}
