package com.roomlog.analysis.service;

import com.roomlog.analysis.domain.Analysis;
import com.roomlog.analysis.dto.AiCompareRequest;
import com.roomlog.analysis.dto.AiDetectionRequest;
import com.roomlog.analysis.dto.AiResultRequest;
import com.roomlog.analysis.dto.CreateAnalysisRequest;
import com.roomlog.analysis.dto.CreateAnalysisResponse;
import com.roomlog.analysis.dto.DeleteAnalysisResponse;
import com.roomlog.analysis.dto.GetAnalysisResponse;
import com.roomlog.analysis.dto.GetAnalysisStatusResponse;
import com.roomlog.analysis.dto.GetComparisonAnalysisListResponse;
import com.roomlog.defect.domain.Defect;
import com.roomlog.analysis.repository.AnalysisRepository;
import com.roomlog.defect.dto.DefectItemResponse;
import com.roomlog.defect.service.RepairCostCalculator;
import com.roomlog.defect.service.SelfRepairService;
import com.roomlog.defect.repository.DefectRepository;
import com.roomlog.global.exception.CustomException;
import com.roomlog.global.exception.ErrorCode;
import com.roomlog.global.infra.AiClient;
import com.roomlog.house.repository.HouseRepository;
import com.roomlog.room.domain.Room;
import com.roomlog.room.repository.RoomRepository;
import com.roomlog.scan.domain.Scan;
import com.roomlog.scan.repository.ScanRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.Duration;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisService {

    private final AnalysisRepository analysisRepository;
    private final RoomRepository roomRepository;
    private final HouseRepository houseRepository;
    private final ScanRepository scanRepository;
    private final DefectRepository defectRepository;
    private final SelfRepairService selfRepairService;
    private final RepairCostCalculator repairCostCalculator;
    private final AiClient aiClient;
    private final TransactionTemplate transactionTemplate;

    @Transactional(readOnly = true)
    public GetAnalysisResponse getAnalysis(Long userId, Long analysisId) {
        Analysis analysis = analysisRepository.findById(analysisId)
                .orElseThrow(() -> new CustomException(ErrorCode.ANALYSIS_001));

        Room room = roomRepository.findById(analysis.getRoomId())
                .orElseThrow(() -> new CustomException(ErrorCode.ROOM_001));

        houseRepository.findByIdAndUserId(room.getHouseId(), userId)
                .orElseThrow(() -> new CustomException(ErrorCode.ROOM_002));

        if (analysis.getStatus() != Analysis.Status.COMPLETED) {
            throw new CustomException(ErrorCode.ANALYSIS_004);
        }

        List<Defect> defects = defectRepository.findByAnalysisId(analysisId);

        // 사용자가 하자를 눌러 상세를 열기 전에 자가 수리 안내를 미리 만들어둔다(백그라운드, 응답을 막지 않음).
        defects.forEach(selfRepairService::prefetchGuide);

        return GetAnalysisResponse.of(analysis, room.getPlyUrl(),
                defects.stream().map(DefectItemResponse::from).toList());
    }

    @Transactional(readOnly = true)
    public GetAnalysisStatusResponse getAnalysisStatus(Long userId, Long analysisId) {
        Analysis analysis = analysisRepository.findById(analysisId)
                .orElseThrow(() -> new CustomException(ErrorCode.ANALYSIS_001));

        Room room = roomRepository.findById(analysis.getRoomId())
                .orElseThrow(() -> new CustomException(ErrorCode.ROOM_001));

        houseRepository.findByIdAndUserId(room.getHouseId(), userId)
                .orElseThrow(() -> new CustomException(ErrorCode.ROOM_002));

        return GetAnalysisStatusResponse.from(analysis);
    }

    @Transactional
    public void receiveAiResult(Long analysisId, AiResultRequest request) {
        Analysis analysis = analysisRepository.findById(analysisId)
                .orElseThrow(() -> new CustomException(ErrorCode.ANALYSIS_001));

        if (analysis.getStatus() != Analysis.Status.PENDING) {
            throw new CustomException(ErrorCode.ANALYSIS_002);
        }

        if (!request.isSuccess()) {
            analysis.fail();
            return;
        }

        Map<String, String> imageUrlBySourceDefect = sourceDefectImageUrls(analysis);

        // 면적(㎠)이 0이거나 없는 하자는 AI가 측정에 실패한 것이라 저장하지 않는다. 앱에도 안 내려가고 비용에도 안 들어간다.
        // 면적은 AI가 준 ㎠ 그대로 저장하고 앱에도 ㎠로 내려준다. ㎡ 환산은 비용 계산 안에서만 한다.
        List<Defect> defects = request.getDefects() == null ? List.of() : request.getDefects().stream()
                .filter(item -> item.getArea() != null && item.getArea() > 0)
                .map(item -> {
                    // 하자별 금액에는 출장비를 넣지 않는다. 출장비는 총액에 한 번만 더한다.
                    int estimatedCost = repairCostCalculator.defectCost(item.getType(), item.getSeverity(), item.getArea());

                    return Defect.builder()
                            .analysisId(analysisId)
                            .type(item.getType())
                            .severity(item.getSeverity())
                            .location(item.getLocation())
                            .area(item.getArea())
                            .estimatedCost(estimatedCost)
                            .description(item.getDescription())
                            .imageUrl(item.getImageUrl() != null ? item.getImageUrl()
                                    : imageUrlBySourceDefect.get(defectKey(item.getType(), item.getLocation())))
                            .region3d(item.getRegion3d())
                            .build();
                })
                .toList();

        defectRepository.saveAll(defects);

        analysis.complete(repairCostCalculator.totalCost(defects));
    }

    /**
     * 비교 분석 결과에는 AI가 이미지 URL을 돌려주지 않을 수 있으므로,
     * 비교의 입력이 된 입주 스캔 하자들의 이미지 URL을 (타입, 위치) 기준으로 이어받을 수 있게 모아둔다.
     */
    private Map<String, String> sourceDefectImageUrls(Analysis analysis) {
        if (analysis.getOutScanId() == null) return Map.of();

        return analysisRepository
                .findFirstByInScanIdAndStatusOrderByCreatedAtDesc(analysis.getInScanId(), Analysis.Status.COMPLETED)
                .map(prev -> defectRepository.findByAnalysisId(prev.getId()).stream()
                        .filter(d -> d.getImageUrl() != null)
                        .collect(Collectors.toMap(
                                d -> defectKey(d.getType(), d.getLocation()),
                                Defect::getImageUrl,
                                (first, second) -> first)))
                .orElse(Map.of());
    }

    private String defectKey(String type, String location) {
        return type + "|" + location;
    }

    @Transactional(readOnly = true)
    public List<GetComparisonAnalysisListResponse> getComparisonAnalyses(Long userId, Long houseId) {
        houseRepository.findByIdAndUserId(houseId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.HOUSE_002));

        List<Room> houseRooms = roomRepository.findByHouseId(houseId);
        List<Long> roomIds = houseRooms.stream().map(Room::getId).toList();

        if (roomIds.isEmpty()) return List.of();

        List<Analysis> analyses = analysisRepository.findByRoomIdInAndOutScanIdIsNotNullOrderByCreatedAtDesc(roomIds);

        if (analyses.isEmpty()) return List.of();

        List<Long> analysisIds = analyses.stream().map(Analysis::getId).toList();
        Map<Long, List<DefectItemResponse>> defectsByAnalysisId = defectRepository.findByAnalysisIdIn(analysisIds)
                .stream()
                .map(DefectItemResponse::from)
                .collect(Collectors.groupingBy(DefectItemResponse::getAnalysisId));

        Map<Long, Room> roomById = houseRooms.stream().collect(Collectors.toMap(Room::getId, r -> r));

        List<Long> outScanIds = analyses.stream().map(Analysis::getOutScanId).toList();
        Map<Long, Long> outScanToRoomId = scanRepository.findAllById(outScanIds)
                .stream().collect(Collectors.toMap(Scan::getId, Scan::getRoomId));

        Set<Long> outRoomIds = new HashSet<>(outScanToRoomId.values());
        outRoomIds.removeAll(roomById.keySet());
        roomRepository.findAllById(outRoomIds).forEach(r -> roomById.put(r.getId(), r));

        return analyses.stream().map(analysis -> {
            Room inRoom = roomById.get(analysis.getRoomId());
            Room outRoom = roomById.get(outScanToRoomId.get(analysis.getOutScanId()));
            List<DefectItemResponse> defects = defectsByAnalysisId.getOrDefault(analysis.getId(), List.of());
            return GetComparisonAnalysisListResponse.of(analysis, inRoom, outRoom, defects);
        }).toList();
    }

    @Transactional
    public DeleteAnalysisResponse deleteAnalysis(Long userId, Long analysisId) {
        Analysis analysis = analysisRepository.findById(analysisId)
                .orElseThrow(() -> new CustomException(ErrorCode.ANALYSIS_001));

        Room room = roomRepository.findById(analysis.getRoomId())
                .orElseThrow(() -> new CustomException(ErrorCode.ROOM_001));

        houseRepository.findByIdAndUserId(room.getHouseId(), userId)
                .orElseThrow(() -> new CustomException(ErrorCode.ROOM_002));

        defectRepository.findByAnalysisId(analysisId).forEach(Defect::softDelete);
        analysis.softDelete();

        return DeleteAnalysisResponse.of(analysisId);
    }

    /** 같은 방에 이 시간 안에 만들어진 PENDING 분석이 있으면 새 요청을 거부한다(연타 방지). 이보다 오래 걸린 건 유실로 보고 재요청을 허용한다. */
    private static final Duration IN_PROGRESS_WINDOW = Duration.ofMinutes(10);

    /**
     * 분석 생성. AI 서버 호출은 최대 30초가 걸릴 수 있어 트랜잭션 안에서 하지 않는다.
     * 1) 트랜잭션: 검증 + PENDING 분석 저장 + AI 요청 본문 준비 (커밋되면 콜백이 와도 분석을 찾을 수 있다)
     * 2) 트랜잭션 밖: AI 서버 호출. 실패하면 별도 트랜잭션으로 FAILED 처리
     */
    public CreateAnalysisResponse createAnalysis(Long userId, CreateAnalysisRequest request) {
        PreparedAnalysis prepared = transactionTemplate.execute(status -> prepareAnalysis(userId, request));
        Analysis analysis = prepared.analysis();

        try {
            prepared.aiRequest().run();
        } catch (Exception e) {
            log.error("AI 요청 실패 - analysisId: {}, error: {}", analysis.getId(), e.getMessage(), e);
            markFailed(analysis.getId());
            analysis.fail();
        }

        return CreateAnalysisResponse.of(analysis);
    }

    private record PreparedAnalysis(Analysis analysis, Runnable aiRequest) {}

    private PreparedAnalysis prepareAnalysis(Long userId, CreateAnalysisRequest request) {
        Room room = roomRepository.findById(request.getInRoomId())
                .orElseThrow(() -> new CustomException(ErrorCode.ROOM_001));

        houseRepository.findByIdAndUserId(room.getHouseId(), userId)
                .orElseThrow(() -> new CustomException(ErrorCode.ROOM_002));

        if (analysisRepository.existsByRoomIdAndStatusAndCreatedAtAfter(
                request.getInRoomId(), Analysis.Status.PENDING, LocalDateTime.now().minus(IN_PROGRESS_WINDOW))) {
            throw new CustomException(ErrorCode.ANALYSIS_005);
        }

        Scan inScan = scanRepository.findFirstByRoomIdAndStatusOrderByCreatedAtDesc(request.getInRoomId(), Scan.Status.COMPLETED)
                .orElseThrow(() -> new CustomException(ErrorCode.ANALYSIS_003));

        Scan outScan = null;
        if (request.getOutRoomId() != null) {
            Room outRoom = roomRepository.findById(request.getOutRoomId())
                    .orElseThrow(() -> new CustomException(ErrorCode.ROOM_001));

            houseRepository.findByIdAndUserId(outRoom.getHouseId(), userId)
                    .orElseThrow(() -> new CustomException(ErrorCode.ROOM_002));

            outScan = scanRepository.findFirstByRoomIdAndStatusOrderByCreatedAtDesc(request.getOutRoomId(), Scan.Status.COMPLETED)
                    .orElseThrow(() -> new CustomException(ErrorCode.ANALYSIS_003));
        }

        Analysis analysis = Analysis.builder()
                .roomId(request.getInRoomId())
                .inScanId(inScan.getId())
                .outScanId(outScan != null ? outScan.getId() : null)
                .build();
        analysisRepository.save(analysis);

        String callbackUrl = aiClient.analysisCallbackUrl(analysis.getId());
        if (outScan == null) {
            AiDetectionRequest aiRequest = new AiDetectionRequest(
                    analysis.getId(), inScan.getId(), inScan.getFileUrl(), callbackUrl);
            return new PreparedAnalysis(analysis, () -> aiClient.requestDefectDetection(aiRequest));
        }

        List<AiCompareRequest.DefectItem> inDefects = latestDefectsOf(inScan.getId());
        List<AiCompareRequest.DefectItem> outDefects = latestDefectsOf(outScan.getId());
        AiCompareRequest aiRequest = new AiCompareRequest(
                analysis.getId(),
                inScan.getId(),
                inDefects.isEmpty() ? inScan.getFileUrl() : null,
                inDefects.isEmpty() ? null : inDefects,
                outScan.getId(),
                outDefects.isEmpty() ? outScan.getFileUrl() : null,
                outDefects.isEmpty() ? null : outDefects,
                callbackUrl);
        return new PreparedAnalysis(analysis, () -> aiClient.requestDefectComparison(aiRequest));
    }

    /** 해당 스캔으로 완료된 가장 최근 분석의 하자 목록. 없으면 빈 목록. */
    private List<AiCompareRequest.DefectItem> latestDefectsOf(Long scanId) {
        return analysisRepository
                .findFirstByInScanIdAndStatusOrderByCreatedAtDesc(scanId, Analysis.Status.COMPLETED)
                .map(prev -> defectRepository.findByAnalysisId(prev.getId()).stream()
                        .map(d -> new AiCompareRequest.DefectItem(
                                d.getType(), d.getSeverity(), d.getLocation(),
                                d.getArea(), d.getDescription(), d.getImageUrl(), d.getRegion3d()))
                        .toList())
                .orElse(Collections.emptyList());
    }

    private void markFailed(Long analysisId) {
        transactionTemplate.executeWithoutResult(status ->
                analysisRepository.findById(analysisId).ifPresent(Analysis::fail));
    }

}
