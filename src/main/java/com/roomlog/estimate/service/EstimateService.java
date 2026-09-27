package com.roomlog.estimate.service;

import com.roomlog.analysis.domain.Analysis;
import com.roomlog.analysis.repository.AnalysisRepository;
import com.roomlog.defect.domain.Defect;
import com.roomlog.defect.repository.DefectRepository;
import com.roomlog.defect.service.RepairCostCalculator;
import com.roomlog.estimate.domain.Estimate;
import com.roomlog.estimate.domain.EstimateDefect;
import com.roomlog.estimate.dto.CreateEstimateRequest;
import com.roomlog.estimate.dto.CreateEstimateResponse;
import com.roomlog.estimate.dto.EstimateListItemResponse;
import com.roomlog.estimate.dto.EstimatePreviewRequest;
import com.roomlog.estimate.dto.EstimatePreviewResponse;
import com.roomlog.estimate.dto.GetEstimateDetailResponse;
import com.roomlog.estimate.dto.GetEstimateListResponse;
import com.roomlog.estimate.repository.EstimateDefectRepository;
import com.roomlog.estimate.repository.EstimateRepository;
import com.roomlog.global.exception.CustomException;
import com.roomlog.global.exception.ErrorCode;
import com.roomlog.global.infra.KakaoLocalClient;
import com.roomlog.global.infra.KakaoLocalClient.KakaoPlace;
import com.roomlog.house.repository.HouseRepository;
import com.roomlog.repair.domain.Repair;
import com.roomlog.repair.repository.RepairRepository;
import com.roomlog.room.domain.Room;
import com.roomlog.room.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EstimateService {

    private final AnalysisRepository analysisRepository;
    private final RoomRepository roomRepository;
    private final HouseRepository houseRepository;
    private final DefectRepository defectRepository;
    private final EstimateRepository estimateRepository;
    private final EstimateDefectRepository estimateDefectRepository;
    private final RepairRepository repairRepository;
    private final KakaoLocalClient kakaoLocalClient;
    private final RepairCostCalculator repairCostCalculator;

    @Transactional(readOnly = true)
    public GetEstimateListResponse getEstimateList(Long userId, Long roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.ROOM_001));
        houseRepository.findByIdAndUserId(room.getHouseId(), userId)
                .orElseThrow(() -> new CustomException(ErrorCode.ROOM_002));

        List<Estimate> estimates = estimateRepository.findByRoomId(roomId);
        List<Long> estimateIds = estimates.stream().map(Estimate::getId).toList();

        Map<Long, List<EstimateDefect>> estimateDefectsByEstimateId = estimateDefectRepository
                .findByEstimateIdIn(estimateIds).stream()
                .collect(Collectors.groupingBy(EstimateDefect::getEstimateId));

        List<Long> allDefectIds = estimateDefectsByEstimateId.values().stream()
                .flatMap(List::stream)
                .map(EstimateDefect::getDefectId)
                .distinct()
                .toList();
        Map<Long, Defect> defectById = defectRepository.findAllById(allDefectIds).stream()
                .collect(Collectors.toMap(Defect::getId, d -> d));

        Map<Long, Repair> repairByEstimateId = repairRepository
                .findByEstimateIdIn(estimateIds).stream()
                .collect(Collectors.toMap(Repair::getEstimateId, r -> r, (a, b) -> a));

        List<EstimateListItemResponse> items = estimates.stream()
                .map(e -> {
                    List<Defect> defects = estimateDefectsByEstimateId.getOrDefault(e.getId(), List.of())
                            .stream()
                            .map(ed -> defectById.get(ed.getDefectId()))
                            .filter(Objects::nonNull)
                            .toList();
                    return EstimateListItemResponse.of(e, defects, repairByEstimateId.get(e.getId()));
                })
                .toList();

        return GetEstimateListResponse.of(items);
    }

    @Transactional(readOnly = true)
    public GetEstimateDetailResponse getEstimateDetail(Long userId, Long estimateId) {
        Estimate estimate = estimateRepository.findById(estimateId)
                .orElseThrow(() -> new CustomException(ErrorCode.ESTIMATE_002));

        if (!estimate.getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.ESTIMATE_003);
        }

        List<EstimateDefect> estimateDefects = estimateDefectRepository.findByEstimateId(estimateId);
        return GetEstimateDetailResponse.of(estimate, estimateDefects);
    }

    @Transactional(readOnly = true)
    public EstimatePreviewResponse previewEstimate(Long userId, EstimatePreviewRequest request) {
        Analysis analysis = analysisRepository.findById(request.getAnalysisId())
                .orElseThrow(() -> new CustomException(ErrorCode.ANALYSIS_001));

        Room room = roomRepository.findById(analysis.getRoomId())
                .orElseThrow(() -> new CustomException(ErrorCode.ROOM_001));

        houseRepository.findByIdAndUserId(room.getHouseId(), userId)
                .orElseThrow(() -> new CustomException(ErrorCode.ROOM_002));

        KakaoPlace place = kakaoLocalClient.getPlaceById(request.getProviderExternalId());
        if (place == null) {
            throw new CustomException(ErrorCode.REPAIRSHOP_002);
        }

        List<Defect> defects = defectRepository.findByAnalysisId(request.getAnalysisId());
        int visitFee = repairCostCalculator.visitFee(defects.stream().map(Defect::getType).toList());

        return EstimatePreviewResponse.of(analysis.getId(), room.getId(), place, defects, visitFee, request.getMessage());
    }

    @Transactional
    public CreateEstimateResponse createEstimate(Long userId, CreateEstimateRequest request) {
        Room room = roomRepository.findById(request.getRoomId())
                .orElseThrow(() -> new CustomException(ErrorCode.ROOM_001));

        houseRepository.findByIdAndUserId(room.getHouseId(), userId)
                .orElseThrow(() -> new CustomException(ErrorCode.ROOM_002));

        if (request.getAnalysisId() != null) {
            analysisRepository.findById(request.getAnalysisId())
                    .orElseThrow(() -> new CustomException(ErrorCode.ANALYSIS_001));
        }

        List<Defect> defects = defectRepository.findAllById(request.getDefectIds());
        if (defects.size() != request.getDefectIds().size()) {
            throw new CustomException(ErrorCode.DEFECT_001);
        }

        try {
            Estimate estimate = Estimate.builder()
                    .userId(userId)
                    .roomId(request.getRoomId())
                    .analysisId(request.getAnalysisId())
                    .providerName(request.getProviderName())
                    .providerPhone(request.getProviderPhone())
                    .providerAddress(request.getProviderAddress())
                    .message(request.getMessage())
                    .build();
            estimateRepository.save(estimate);

            List<EstimateDefect> estimateDefects = defects.stream()
                    .map(d -> EstimateDefect.builder()
                            .estimateId(estimate.getId())
                            .defectId(d.getId())
                            .build())
                    .toList();
            estimateDefectRepository.saveAll(estimateDefects);

            return CreateEstimateResponse.of(estimate.getId());
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            throw new CustomException(ErrorCode.ESTIMATE_001);
        }
    }

}
