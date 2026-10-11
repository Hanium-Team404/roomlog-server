package com.roomlog.defect.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.roomlog.defect.domain.Defect;
import com.roomlog.room.domain.Room;
import lombok.Getter;

/**
 * 하자 ID 단건 조회 응답. V01(방 하자 목록)의 defects[] 항목과 같은 필드를 그대로 내려주고,
 * 방 목록 없이 하자 ID만으로 진입하는 화면을 위해 어느 방의 하자인지(room_id)와
 * 하자 이미지가 없을 때 대신 보여줄 방 썸네일(room_image_url)을 덧붙인다.
 */
@Getter
public class GetDefectDetailResponse extends DefectItemResponse {

    @JsonProperty("room_id")
    private final Long roomId;

    /** 방 썸네일. 방에 썸네일이 없으면 null. */
    @JsonProperty("room_image_url")
    private final String roomImageUrl;

    private GetDefectDetailResponse(Defect defect, Room room) {
        super(defect);
        this.roomId = room.getId();
        this.roomImageUrl = room.getThumbnailUrl();
    }

    public static GetDefectDetailResponse of(Defect defect, Room room) {
        return new GetDefectDetailResponse(defect, room);
    }
}
