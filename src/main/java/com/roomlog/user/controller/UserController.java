package com.roomlog.user.controller;

import com.roomlog.global.response.ApiResponse;
import com.roomlog.global.security.LoginUser;
import com.roomlog.user.dto.DeleteUserResponse;
import com.roomlog.user.dto.GetMyProfileResponse;
import com.roomlog.user.dto.UpdateUserRequest;
import com.roomlog.user.dto.UpdateUserResponse;
import com.roomlog.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "M01. 내 프로필 조회", description = "로그인한 사용자의 프로필 정보를 조회합니다.", tags = "7. MyPage")
    @GetMapping
    public ApiResponse<GetMyProfileResponse> getMyProfile(@AuthenticationPrincipal LoginUser loginUser) {
        GetMyProfileResponse response = userService.getMyProfile(loginUser.userId());
        return ApiResponse.success(200, "내 정보 조회에 성공했습니다.", response);
    }

    @Operation(summary = "M02. 내 정보 수정", description = "닉네임을 수정합니다.", tags = "7. MyPage")
    @PatchMapping
    public ApiResponse<UpdateUserResponse> updateUser(@AuthenticationPrincipal LoginUser loginUser,
                                                      @Valid @RequestBody UpdateUserRequest request) {
        UpdateUserResponse response = userService.updateUser(loginUser.userId(), request);
        return ApiResponse.success(200, "내 정보 수정에 성공했습니다.", response);
    }

    @Operation(summary = "M04. 회원 탈퇴", description = "계정과 모든 데이터(집·방·스캔·분석·하자·견적·수리·챗봇 기록·파일)를 즉시 삭제합니다.", tags = "7. MyPage")
    @DeleteMapping
    public ApiResponse<DeleteUserResponse> deleteUser(@AuthenticationPrincipal LoginUser loginUser) {
        DeleteUserResponse response = userService.deleteUser(loginUser.userId());
        return ApiResponse.success(200, "회원 탈퇴가 완료되었습니다.", response);
    }
}
