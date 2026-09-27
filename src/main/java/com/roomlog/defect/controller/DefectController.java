package com.roomlog.defect.controller;

import com.roomlog.defect.dto.GetMainHouseDefectsResponse;
import com.roomlog.defect.dto.GetSelfRepairResponse;
import com.roomlog.defect.service.DefectService;
import com.roomlog.defect.service.SelfRepairService;
import com.roomlog.global.response.ApiResponse;
import com.roomlog.global.security.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/defects")
@RequiredArgsConstructor
public class DefectController {

    private final SelfRepairService selfRepairService;
    private final DefectService defectService;

    @Operation(summary = "C04. 등록된 하자 목록 조회", description = """
            대표 집에 등록된 하자를 최신순으로 모두 반환합니다. 챗봇의 "등록된 하자에서 선택하기" 바텀시트에 사용합니다.

            각 항목의 image_url은 하자 탐지 시 저장된 이미지이며, 없으면 null입니다. 이 경우 앱에서 기본 썸네일로 대체해주세요.
            room_name은 하자가 속한 방 이름(예: 거실), location은 방 안에서의 위치 상세(예: 벽면 북서부)입니다.

            대표 집이 설정돼 있지 않거나 등록된 하자가 없으면 defect_count 0, defects 빈 배열로 정상 응답합니다.

            사용자가 목록에서 하자를 고르면 C02에 defect_id를 실어 보내주세요.""", tags = "8. Chat")
    @GetMapping("/main-house")
    public ApiResponse<GetMainHouseDefectsResponse> getMainHouseDefects(
            @AuthenticationPrincipal LoginUser loginUser) {

        GetMainHouseDefectsResponse response = defectService.getMainHouseDefects(loginUser.userId());
        return ApiResponse.success(200, "하자 목록 조회에 성공했습니다.", response);
    }

    @Operation(summary = "V05. 자가 수리 안내 조회", description = """
            하자 ID로 자가 수리 가능 여부와 안내 정보를 조회합니다. 하자 상세 화면에서 사용합니다.

            [가능한 경우] description(수리 가능 사유), videos(유튜브 예시 영상 최대 2건, 검색 결과가 없으면 빈 배열), items(구매 필요 목록: 이름, 이미지, 쿠팡 검색 링크)가 내려갑니다.

            [불가능한 경우] description(수리 불가 사유)만 내려가며 videos와 items는 빈 배열입니다.

            최초 호출 시에만 GPT로 생성되며 이후에는 저장된 결과를 반환합니다. 생성에 수 초가 걸릴 수 있습니다.""", tags = "4. Viewer")
    @GetMapping("/{defectId}/self-repair")
    public ApiResponse<GetSelfRepairResponse> getSelfRepairGuide(
            @AuthenticationPrincipal LoginUser loginUser,
            @Parameter(description = "조회할 하자 ID", example = "12") @PathVariable Long defectId) {

        GetSelfRepairResponse response = selfRepairService.getSelfRepairGuide(loginUser.userId(), defectId);
        return ApiResponse.success(200, "자가 수리 안내 조회에 성공했습니다.", response);
    }
}
