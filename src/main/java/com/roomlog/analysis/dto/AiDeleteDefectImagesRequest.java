package com.roomlog.analysis.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class AiDeleteDefectImagesRequest {

    @JsonProperty("image_urls")
    private List<String> imageUrls;
}
