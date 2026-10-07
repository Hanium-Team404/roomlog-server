package com.roomlog.scan.service;

import com.roomlog.global.exception.CustomException;
import com.roomlog.global.exception.ErrorCode;
import com.roomlog.global.infra.AiClient;
import com.roomlog.global.infra.R2FileUploader;
import com.roomlog.house.repository.HouseRepository;
import com.roomlog.room.repository.RoomRepository;
import com.roomlog.scan.domain.Scan;

import com.roomlog.scan.dto.AiReconstructionRequest;
import com.roomlog.scan.dto.AiReconstructionResult;
import com.roomlog.scan.dto.CreateScanRequest;
import com.roomlog.scan.dto.CreateScanResponse;
import com.roomlog.scan.dto.GetScanResponse;
import com.roomlog.scan.dto.GetScanStatusResponse;
import com.roomlog.scan.repository.ScanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScanService {

    private final ScanRepository scanRepository;
    private final HouseRepository houseRepository;
    private final RoomRepository roomRepository;
    private final R2FileUploader r2FileUploader;
    private final AiClient aiClient;
    private final TransactionTemplate transactionTemplate;

    /**
     * 스캔 업로드. R2 업로드와 AI 서버 호출은 수십 초가 걸릴 수 있어 트랜잭션 안에서 하지 않는다.
     * 1) 트랜잭션: 검증 + SCANNING 스캔 저장  2) R2 업로드  3) 트랜잭션: 파일 URL 저장
     * 4) AI 서버 호출. 실패하면 별도 트랜잭션으로 FAILED 처리
     */
    public CreateScanResponse uploadScan(Long userId, MultipartFile file, CreateScanRequest request) {
        if (file == null || file.isEmpty()) {
            throw new CustomException(ErrorCode.COMMON_400, "스캔 파일이 없습니다.");
        }

        Long scanId = transactionTemplate.execute(status -> {
            houseRepository.findByIdAndUserId(request.getHouseId(), userId)
                    .orElseThrow(() -> new CustomException(ErrorCode.COMMON_403));

            Scan scan = Scan.builder()
                    .userId(userId)
                    .houseId(request.getHouseId())
                    .status(Scan.Status.SCANNING)
                    .build();
            return scanRepository.save(scan).getId();
        });

        String originalFilename = file.getOriginalFilename();
        String extension = (originalFilename != null && originalFilename.contains("."))
                ? originalFilename.substring(originalFilename.lastIndexOf("."))
                : ".zip";
        String key = "scans/" + scanId + "/model" + extension;

        String fileUrl;
        try {
            fileUrl = r2FileUploader.upload(file, key);
        } catch (Exception e) {
            markFailed(scanId);
            throw e;
        }

        Scan scan = transactionTemplate.execute(status -> {
            Scan s = scanRepository.findById(scanId)
                    .orElseThrow(() -> new CustomException(ErrorCode.SCAN_001));
            s.updateFileUrl(fileUrl);
            return s;
        });

        try {
            aiClient.requestReconstruction(new AiReconstructionRequest(
                    scanId, fileUrl, aiClient.scanCallbackUrl(scanId)));
        } catch (Exception e) {
            log.error("AI 재구성 요청 실패 - scanId: {}, error: {}", scanId, e.getMessage(), e);
            markFailed(scanId);
            scan.fail();
        }

        return CreateScanResponse.from(scan);
    }

    private void markFailed(Long scanId) {
        transactionTemplate.executeWithoutResult(status ->
                scanRepository.findById(scanId).ifPresent(Scan::fail));
    }

    @Transactional(readOnly = true)
    public GetScanResponse getScanPreview(Long userId, Long scanId) {
        Scan scan = scanRepository.findById(scanId)
                .orElseThrow(() -> new CustomException(ErrorCode.SCAN_001));

        if (!scan.getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.COMMON_403);
        }

        if (scan.getStatus() != Scan.Status.COMPLETED) {
            throw new CustomException(ErrorCode.SCAN_004);
        }

        return GetScanResponse.from(scan);
    }

@Transactional
    public void receiveReconstructionResult(Long scanId, AiReconstructionResult result) {
        Scan scan = scanRepository.findById(scanId)
                .orElseThrow(() -> new CustomException(ErrorCode.SCAN_001));

        // 1차 콜백(mesh 완료): plyUrl 저장 후 COMPLETED 처리
        if (scan.getStatus() == Scan.Status.SCANNING) {
            scan.updatePlyUrl(result.getScanUrl());
            if (result.getThumbnailUrl() != null) {
                scan.updateThumbnailUrl(result.getThumbnailUrl());
            }
            scan.complete();
            return;
        }

        // 2차 콜백(썸네일 완료): plyUrl은 건드리지 않고 썸네일만 갱신
        if (scan.getStatus() == Scan.Status.COMPLETED && result.getThumbnailUrl() != null) {
            scan.updateThumbnailUrl(result.getThumbnailUrl());

            // 2차 콜백 도착 전에 방이 생성됐다면 Room의 썸네일이 null로 남으므로 함께 갱신
            if (scan.getRoomId() != null) {
                roomRepository.findById(scan.getRoomId())
                        .ifPresent(room -> room.updateThumbnailUrl(result.getThumbnailUrl()));
            }
            return;
        }

        throw new CustomException(ErrorCode.SCAN_004);
    }

    @Transactional
    public void cancelScan(Long userId, Long scanId) {
        Scan scan = scanRepository.findById(scanId)
                .orElseThrow(() -> new CustomException(ErrorCode.SCAN_001));

        if (!scan.getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.COMMON_403);
        }

        if (scan.getStatus() != Scan.Status.SCANNING) {
            throw new CustomException(ErrorCode.SCAN_005);
        }

        scan.fail();
    }

    @Transactional(readOnly = true)
    public GetScanStatusResponse getScanStatus(Long userId, Long scanId) {
        Scan scan = scanRepository.findById(scanId)
                .orElseThrow(() -> new CustomException(ErrorCode.SCAN_001));

        if (!scan.getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.COMMON_403);
        }

        return GetScanStatusResponse.from(scan);
    }
}
