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

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class KakaoImageClient {

    private static final String IMAGE_SEARCH_URL = "https://dapi.kakao.com/v2/search/image";
    private static final int SEARCH_SIZE = 10;

    private final RestTemplate restTemplate;

    @Value("${kakao.api-key}")
    private String apiKey;

    /**
     * 검색어로 찾은 이미지 중 앱에서 바로 띄울 수 있는 https URL 하나.
     * http 주소는 iOS(ATS)·Android(cleartext 차단)에서 로드되지 않으므로 https만 고른다.
     * https 결과가 없으면 http 주소를 https로 바꿔 실제로 열리는지 확인한 뒤 쓴다.
     * 결과가 없거나 호출에 실패하면 null.
     */
    public String searchFirstImageUrl(String query) {
        if (query == null || query.isBlank()) return null;

        URI uri = UriComponentsBuilder.fromHttpUrl(IMAGE_SEARCH_URL)
                .queryParam("query", query)
                .queryParam("size", SEARCH_SIZE)
                .encode()
                .build()
                .toUri();

        try {
            ResponseEntity<ImageSearchResponse> response = restTemplate.exchange(
                    uri, HttpMethod.GET, authHeader(), ImageSearchResponse.class);
            ImageSearchResponse body = response.getBody();
            if (body == null || body.getDocuments() == null || body.getDocuments().isEmpty()) return null;

            List<String> urls = body.getDocuments().stream()
                    .map(ImageDocument::getImageUrl)
                    .filter(url -> url != null && !url.isBlank())
                    .toList();

            for (String url : urls) {
                if (url.startsWith("https://")) return url;
            }
            for (String url : urls) {
                if (url.startsWith("http://")) {
                    String https = "https://" + url.substring("http://".length());
                    if (isReachable(https)) return https;
                }
            }
            return null;
        } catch (RestClientException e) {
            log.error("Kakao image search API error: {}", e.getMessage());
            return null;
        }
    }

    /** https로 바꾼 주소가 실제로 열리는지 HEAD 요청으로 확인한다. */
    private boolean isReachable(String url) {
        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setRequestMethod("HEAD");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            conn.setInstanceFollowRedirects(true);
            int code = conn.getResponseCode();
            conn.disconnect();
            return code >= 200 && code < 400;
        } catch (IOException e) {
            return false;
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
