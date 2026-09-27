package com.roomlog.global.infra;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class KakaoImageClient {

    private static final String IMAGE_SEARCH_URL = "https://dapi.kakao.com/v2/search/image";

    private final RestTemplate restTemplate;

    @Value("${kakao.api-key}")
    private String apiKey;

    /** 검색어로 찾은 첫 번째 이미지 URL. 결과가 없거나 호출에 실패하면 null. */
    public String searchFirstImageUrl(String query) {
        if (query == null || query.isBlank()) return null;

        URI uri = UriComponentsBuilder.fromHttpUrl(IMAGE_SEARCH_URL)
                .queryParam("query", query)
                .queryParam("size", 1)
                .encode()
                .build()
                .toUri();

        try {
            ResponseEntity<ImageSearchResponse> response = restTemplate.exchange(
                    uri, HttpMethod.GET, authHeader(), ImageSearchResponse.class);
            ImageSearchResponse body = response.getBody();
            if (body == null || body.getDocuments() == null || body.getDocuments().isEmpty()) return null;
            return body.getDocuments().get(0).getImageUrl();
        } catch (RestClientException e) {
            log.error("Kakao image search API error: {}", e.getMessage());
            return null;
        }
    }

    private HttpEntity<Void> authHeader() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "KakaoAK " + apiKey);
        return new HttpEntity<>(headers);
    }

    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ImageSearchResponse {
        private List<ImageDocument> documents;
    }

    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ImageDocument {
        @JsonProperty("image_url")
        private String imageUrl;
    }
}
