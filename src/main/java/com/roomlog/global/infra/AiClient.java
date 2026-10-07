package com.roomlog.global.infra;

import com.roomlog.analysis.dto.AiCompareRequest;
import com.roomlog.analysis.dto.AiDeleteDefectImagesRequest;
import com.roomlog.analysis.dto.AiDetectionRequest;
import com.roomlog.scan.dto.AiReconstructionRequest;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Slf4j
@Component
public class AiClient {

    private static final String API_KEY_HEADER = "X-Api-Key";

    private final RestTemplate restTemplate;

    @Value("${ai.server-url}")
    private String aiServerUrl;

    @Value("${ai.api-key}")
    private String aiApiKey;

    @Value("${ai.callback-base-url}")
    private String callbackBaseUrl;

    public AiClient(@Qualifier("aiRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public String analysisCallbackUrl(Long analysisId) {
        return callbackBaseUrl + "/analyses/" + analysisId + "/result";
    }

    public String scanCallbackUrl(Long scanId) {
        return callbackBaseUrl + "/scans/" + scanId + "/result";
    }

    public void requestReconstruction(AiReconstructionRequest request) {
        restTemplate.postForObject(aiServerUrl + "/reconstruction", authEntity(request), Void.class);
    }

    public void requestDefectDetection(AiDetectionRequest request) {
        restTemplate.postForObject(aiServerUrl + "/defect-detection", authEntity(request), Void.class);
    }

    public void requestDefectComparison(AiCompareRequest request) {
        restTemplate.postForObject(aiServerUrl + "/defect-comparison", authEntity(request), Void.class);
    }

    /** 스캔 하나가 AI 서버 S3에 남긴 파일(3D 모델·썸네일 등)을 모두 지운다. */
    public void deleteScanFiles(Long scanId) {
        restTemplate.exchange(aiServerUrl + "/scans/" + scanId, HttpMethod.DELETE, authEntity(null), Void.class);
    }

    /** 하자 이미지 URL 목록을 AI 서버 S3에서 지운다. AI 서버 주소 형식이 아닌 URL은 건너뛰고 skipped로 돌려준다. */
    public void deleteDefectImages(List<String> imageUrls) {
        if (imageUrls.isEmpty()) {
            return;
        }
        JsonNode body = restTemplate.exchange(aiServerUrl + "/defects", HttpMethod.DELETE,
                authEntity(new AiDeleteDefectImagesRequest(imageUrls)), JsonNode.class).getBody();
        JsonNode skipped = body == null ? null : body.path("data").path("skipped");
        if (skipped != null && skipped.isArray() && !skipped.isEmpty()) {
            log.warn("AI 서버가 삭제를 건너뛴 하자 이미지 URL(수동 정리 필요): {}", skipped);
        }
    }

    private <T> HttpEntity<T> authEntity(T body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(API_KEY_HEADER, aiApiKey);
        return new HttpEntity<>(body, headers);
    }
}
