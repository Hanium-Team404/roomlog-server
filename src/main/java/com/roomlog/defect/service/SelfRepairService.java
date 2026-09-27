package com.roomlog.defect.service;

import com.roomlog.analysis.domain.Analysis;
import com.roomlog.analysis.repository.AnalysisRepository;
import com.roomlog.defect.domain.Defect;
import com.roomlog.defect.domain.DefectRepairGuide;
import com.roomlog.defect.dto.GetSelfRepairResponse;
import com.roomlog.defect.dto.RepairItem;
import com.roomlog.defect.dto.RepairVideo;
import com.roomlog.defect.repository.DefectRepairGuideRepository;
import com.roomlog.defect.domain.RepairSupply;
import com.roomlog.defect.repository.DefectRepository;
import com.roomlog.defect.repository.RepairSupplyRepository;
import com.roomlog.global.exception.CustomException;
import com.roomlog.global.exception.ErrorCode;
import com.roomlog.global.infra.GptClient;
import com.roomlog.global.infra.YoutubeClient;
import com.roomlog.house.repository.HouseRepository;
import com.roomlog.room.domain.Room;
import com.roomlog.room.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SelfRepairService {

    /** 하자 하나에 붙일 수리 방법 영상 개수. */
    private static final int VIDEO_COUNT = 2;

    private final DefectRepository defectRepository;
    private final DefectRepairGuideRepository defectRepairGuideRepository;
    private final AnalysisRepository analysisRepository;
    private final RoomRepository roomRepository;
    private final HouseRepository houseRepository;
    private final GptClient gptClient;
    private final YoutubeClient youtubeClient;
    private final RepairSupplyRepository repairSupplyRepository;

    /**
     * 하나의 트랜잭션으로 묶지 않는다. 생성에 수 초 걸리는 GPT 호출이 커넥션을 잡고 있게 되고,
     * 미리 생성(prefetch)과 동시에 저장될 때 중복 키 예외로 트랜잭션 전체가 롤백되기 때문이다.
     */
    public GetSelfRepairResponse getSelfRepairGuide(Long userId, Long defectId) {
        Defect defect = defectRepository.findById(defectId)
                .orElseThrow(() -> new CustomException(ErrorCode.DEFECT_001));

        validateOwnership(userId, defect);

        DefectRepairGuide guide = defectRepairGuideRepository.findById(defectId)
                .orElseGet(() -> generateAndSave(defect));

        return GetSelfRepairResponse.from(refreshItemsIfImageMissing(retryVideoIfMissing(guide)));
    }

    /**
     * 하자 목록 조회 시 안내를 미리 만들어둔다. 사용자가 하자 상세를 열었을 때 기다리지 않도록 하는 용도다.
     * 실패해도 사용자가 안내를 열 때 그 자리에서 생성되므로 예외를 밖으로 던지지 않는다.
     */
    @Async("selfRepairExecutor")
    public void prefetchGuide(Defect defect) {
        if (defectRepairGuideRepository.existsById(defect.getId())) return;

        try {
            generateAndSave(defect);
        } catch (Exception e) {
            log.warn("self-repair prefetch failed. defectId={}, reason={}", defect.getId(), e.getMessage());
        }
    }

    private DefectRepairGuide generateAndSave(Defect defect) {
        DefectRepairGuide guide = generate(defect);
        try {
            return defectRepairGuideRepository.save(guide);
        } catch (DataIntegrityViolationException e) {
            // 미리 생성 작업이 먼저 저장한 경우. 저장된 쪽을 그대로 쓴다.
            return defectRepairGuideRepository.findById(defect.getId())
                    .orElseThrow(() -> new CustomException(ErrorCode.DEFECT_002));
        }
    }

    private DefectRepairGuide generate(Defect defect) {
        // 자가 수리 가능 여부는 모델이 하자 정보를 보고 직접 판단한다.
        GptClient.SelfRepairGuide generated = gptClient.generateSelfRepairGuide(
                defect.getType(), defect.getSeverity(), defect.getLocation(),
                defect.getArea(), defect.getDescription());

        if (!generated.isSelfRepairPossible()) {
            return DefectRepairGuide.builder()
                    .defectId(defect.getId())
                    .selfRepairPossible(false)
                    .description(generated.getDescription())
                    .items(List.of())
                    .build();
        }

        // GPT는 판정과 사유, 영상 검색어까지 만든다. 영상은 유튜브에서, 준비물은 준비물 테이블에서 가져온다.
        List<RepairVideo> videos = searchVideos(generated.getVideoSearchQuery());
        List<RepairItem> items = supplies(defect.getType());

        return DefectRepairGuide.builder()
                .defectId(defect.getId())
                .selfRepairPossible(true)
                .description(generated.getDescription())
                .videos(videos)
                .videoSearchQuery(generated.getVideoSearchQuery())
                .items(items)
                .build();
    }

    /**
     * 자가 수리 가능 여부만 필요할 때 쓴다(챗봇). 안내가 아직 없으면 그 자리에서 만들어 저장한다.
     * 챗봇 답변과 자가 수리 안내가 서로 다른 판정을 말하지 않도록 저장된 같은 안내를 근거로 쓴다.
     * 접근 권한 확인은 호출하는 쪽 책임이다.
     */
    public boolean isSelfRepairPossible(Defect defect) {
        return defectRepairGuideRepository.findById(defect.getId())
                .orElseGet(() -> generateAndSave(defect))
                .isSelfRepairPossible();
    }

    /**
     * 유튜브 검색에 실패해 영상이 비어 있는 안내는 조회 시점에 한 번 더 검색한다.
     * (일시적인 API 오류나 하루 검색 한도 초과로 비어 있는 경우가 있다.)
     * GPT는 다시 호출하지 않고 저장해 둔 검색어로 유튜브만 다시 찾는다.
     */
    private DefectRepairGuide retryVideoIfMissing(DefectRepairGuide guide) {
        if (!guide.isSelfRepairPossible() || guide.hasVideo()) return guide;
        if (guide.getVideoSearchQuery() == null || guide.getVideoSearchQuery().isBlank()) return guide;

        List<RepairVideo> videos = searchVideos(guide.getVideoSearchQuery());
        if (videos.isEmpty()) return guide;

        guide.updateVideos(videos);
        return defectRepairGuideRepository.save(guide);
    }

    /**
     * 준비물은 안내를 만들 때 복사해 저장하므로, 이미지가 채워지기 전에 만든 안내에는 이미지가 비어 있다.
     * 비어 있는 준비물이 있으면 준비물 테이블에서 다시 읽어 바꿔 저장한다.
     */
    private DefectRepairGuide refreshItemsIfImageMissing(DefectRepairGuide guide) {
        if (!guide.isSelfRepairPossible() || guide.getItems() == null) return guide;
        boolean missing = guide.getItems().stream()
                .anyMatch(item -> item.getImageUrl() == null || item.getImageUrl().isBlank());
        if (!missing) return guide;

        Defect defect = defectRepository.findById(guide.getDefectId()).orElse(null);
        if (defect == null) return guide;

        List<RepairItem> items = supplies(defect.getType());
        boolean filled = items.stream().anyMatch(item -> item.getImageUrl() != null);
        if (!filled) return guide;

        guide.updateItems(items);
        return defectRepairGuideRepository.save(guide);
    }

    private List<RepairVideo> searchVideos(String searchQuery) {
        return youtubeClient.search(searchQuery, VIDEO_COUNT).stream()
                .map(video -> new RepairVideo(video.title(), video.url(), video.thumbnailUrl(), video.channel()))
                .toList();
    }

    /** 하자 종류에 맞는 준비물 목록. 등록된 준비물이 없으면 빈 목록. */
    private List<RepairItem> supplies(String defectType) {
        return repairSupplyRepository.findByDefectTypeOrderBySortOrderAsc(defectType).stream()
                .map(supply -> new RepairItem(
                        supply.getName(), supply.getImageUrl(), supply.getPurchaseUrl()))
                .toList();
    }

    private void validateOwnership(Long userId, Defect defect) {
        Analysis analysis = analysisRepository.findById(defect.getAnalysisId())
                .orElseThrow(() -> new CustomException(ErrorCode.ANALYSIS_001));

        Room room = roomRepository.findById(analysis.getRoomId())
                .orElseThrow(() -> new CustomException(ErrorCode.ROOM_001));

        houseRepository.findByIdAndUserId(room.getHouseId(), userId)
                .orElseThrow(() -> new CustomException(ErrorCode.ROOM_002));
    }

}
