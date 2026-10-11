package com.roomlog.defect.service;

import com.roomlog.analysis.domain.Analysis;
import com.roomlog.analysis.repository.AnalysisRepository;
import com.roomlog.defect.domain.Defect;
import com.roomlog.defect.dto.DefectChatContext;
import com.roomlog.defect.dto.DefectItemResponse;
import com.roomlog.defect.dto.GetDefectDetailResponse;
import com.roomlog.defect.dto.GetDefectEntryResponse;
import com.roomlog.defect.dto.GetMainHouseDefectsResponse;
import com.roomlog.defect.dto.MainHouseDefectItemResponse;
import com.roomlog.defect.repository.DefectRepository;
import com.roomlog.global.exception.CustomException;
import com.roomlog.global.exception.ErrorCode;
import com.roomlog.house.repository.HouseRepository;
import com.roomlog.room.domain.Room;
import com.roomlog.room.repository.RoomRepository;
import com.roomlog.user.domain.User;
import com.roomlog.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DefectService {

    private final RoomRepository roomRepository;
    private final HouseRepository houseRepository;
    private final UserRepository userRepository;
    private final DefectRepository defectRepository;
    private final AnalysisRepository analysisRepository;
    private final SelfRepairService selfRepairService;

    @Transactional(readOnly = true)
    public GetDefectEntryResponse getDefectEntry(Long userId, Long roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.ROOM_001));

        houseRepository.findByIdAndUserId(room.getHouseId(), userId)
                .orElseThrow(() -> new CustomException(ErrorCode.ROOM_002));

        List<Long> analysisIds = analysisRepository.findByRoomId(roomId).stream()
                .map(a -> a.getId())
                .toList();

        List<Defect> defects = analysisIds.isEmpty()
                ? List.of()
                : defectRepository.findByAnalysisIdIn(analysisIds);

        // 사용자가 하자를 눌러 상세를 열기 전에 자가 수리 안내를 미리 만들어둔다(백그라운드, 응답을 막지 않음).
        defects.forEach(selfRepairService::prefetchGuide);

        return GetDefectEntryResponse.from(room, defects.stream().map(DefectItemResponse::from).toList());
    }

    /**
     * 하자 ID 하나로 상세 정보를 반환한다. 방 하자 목록(V01)을 거치지 않고 하자 ID만 들고 상세 화면에 들어올 때 쓴다.
     * 하자 → 분석 → 방 → 집 순으로 따라가 요청한 사용자의 집에 속한 하자인지 확인한다.
     */
    @Transactional(readOnly = true)
    public GetDefectDetailResponse getDefectDetail(Long userId, Long defectId) {
        Defect defect = defectRepository.findById(defectId)
                .orElseThrow(() -> new CustomException(ErrorCode.DEFECT_001));

        Analysis analysis = analysisRepository.findById(defect.getAnalysisId())
                .orElseThrow(() -> new CustomException(ErrorCode.ANALYSIS_001));

        Room room = roomRepository.findById(analysis.getRoomId())
                .orElseThrow(() -> new CustomException(ErrorCode.ROOM_001));

        if (houseRepository.findByIdAndUserId(room.getHouseId(), userId).isEmpty()) {
            throw new CustomException(ErrorCode.DEFECT_003);
        }

        // 상세 화면에서 바로 이어지는 자가 수리 안내(V05)를 미리 만들어둔다(백그라운드, 응답을 막지 않음).
        selfRepairService.prefetchGuide(defect);

        return GetDefectDetailResponse.of(defect, room);
    }

    /**
     * 대표 집에 등록된 하자를 최신순으로 모두 반환한다. 챗봇의 "등록된 하자에서 선택하기" 목록에 쓴다.
     * 대표 집이 없거나 하자가 하나도 없으면 빈 목록을 정상 응답으로 내려준다.
     *
     * Defect에는 등록 시각이 없어 하자를 만든 분석(Analysis)의 생성 시각을 기준으로 정렬한다.
     * 같은 분석에서 나온 하자들은 시각이 같으므로 나중에 저장된 것(= id가 큰 것)을 앞에 둔다.
     */
    @Transactional(readOnly = true)
    public GetMainHouseDefectsResponse getMainHouseDefects(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.COMMON_404));

        Long mainHouseId = user.getMainHouseId();
        if (mainHouseId == null) return GetMainHouseDefectsResponse.from(List.of());

        // 대표 집이 이미 삭제됐거나 남의 집을 가리키는 경우를 걸러낸다.
        if (houseRepository.findByIdAndUserId(mainHouseId, userId).isEmpty()) {
            return GetMainHouseDefectsResponse.from(List.of());
        }

        List<Room> rooms = roomRepository.findByHouseId(mainHouseId);
        if (rooms.isEmpty()) return GetMainHouseDefectsResponse.from(List.of());

        Map<Long, Room> roomsById = rooms.stream()
                .collect(Collectors.toMap(Room::getId, Function.identity()));

        List<Analysis> analyses = analysisRepository.findByRoomIdIn(List.copyOf(roomsById.keySet()));
        if (analyses.isEmpty()) return GetMainHouseDefectsResponse.from(List.of());

        Map<Long, Analysis> analysesById = analyses.stream()
                .collect(Collectors.toMap(Analysis::getId, Function.identity()));

        List<MainHouseDefectItemResponse> items =
                defectRepository.findByAnalysisIdIn(List.copyOf(analysesById.keySet())).stream()
                        .sorted(Comparator
                                .comparing((Defect defect) -> analysesById.get(defect.getAnalysisId()).getCreatedAt())
                                .thenComparing(Defect::getId)
                                .reversed())
                        .map(defect -> {
                            Analysis analysis = analysesById.get(defect.getAnalysisId());
                            Room room = roomsById.get(analysis.getRoomId());
                            return MainHouseDefectItemResponse.of(
                                    defect, room.getId(), room.getName(), analysis.getCreatedAt());
                        })
                        .toList();

        return GetMainHouseDefectsResponse.from(items);
    }

    /**
     * 챗봇이 하자를 근거로 답하기 위해 필요한 정보를 모아 반환한다.
     * 목록을 대표 집 기준으로 내려주므로 접근 권한도 대표 집 기준으로 확인한다.
     *
     * 하나의 트랜잭션으로 묶지 않는다. 자가 수리 판정이 아직 없으면 여기서 GPT 호출과 저장이 일어나는데,
     * 읽기 전용 트랜잭션 안에서는 저장이 반영되지 않고 수 초짜리 호출이 커넥션을 잡고 있게 된다.
     */
    public DefectChatContext getChatContext(Long userId, Long defectId) {
        Defect defect = defectRepository.findById(defectId)
                .orElseThrow(() -> new CustomException(ErrorCode.DEFECT_001));

        Analysis analysis = analysisRepository.findById(defect.getAnalysisId())
                .orElseThrow(() -> new CustomException(ErrorCode.ANALYSIS_001));

        Room room = roomRepository.findById(analysis.getRoomId())
                .orElseThrow(() -> new CustomException(ErrorCode.ROOM_001));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.COMMON_404));

        if (user.getMainHouseId() == null || !user.getMainHouseId().equals(room.getHouseId())) {
            throw new CustomException(ErrorCode.DEFECT_003);
        }

        return new DefectChatContext(
                defect.getId(),
                defect.getType(),
                defect.getSeverity(),
                defect.getLocation(),
                defect.getArea(),
                defect.getDescription(),
                room.getName(),
                selfRepairService.isSelfRepairPossible(defect));
    }
}
