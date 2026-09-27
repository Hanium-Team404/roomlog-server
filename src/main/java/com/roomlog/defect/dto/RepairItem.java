package com.roomlog.defect.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 기존에 저장된 안내에는 price가 남아 있어, 모르는 필드는 무시하고 읽는다. */
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class RepairItem {

    /** 준비물 이름 */
    private String name;

    @JsonProperty("image_url")
    private String imageUrl;

    /** 구매처 링크 */
    private String url;
}
