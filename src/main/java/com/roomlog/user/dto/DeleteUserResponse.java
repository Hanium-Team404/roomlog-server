package com.roomlog.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public class DeleteUserResponse {

    @JsonProperty("user_id")
    private final Long userId;

    @JsonProperty("is_deleted")
    private final boolean isDeleted;

    private DeleteUserResponse(Long userId, boolean isDeleted) {
        this.userId = userId;
        this.isDeleted = isDeleted;
    }

    public static DeleteUserResponse of(Long userId) {
        return new DeleteUserResponse(userId, true);
    }
}
