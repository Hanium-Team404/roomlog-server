package com.roomlog.global.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class AppConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 외부 API(GPT·카카오·유튜브) 공용 RestTemplate.
     * 기본값은 타임아웃이 무제한이라 상대가 응답하지 않으면 요청 스레드가 영영 물린다.
     */
    @Bean
    @Primary
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(30))
                .build();
    }

    /**
     * AI 서버 전용 RestTemplate.
     * AI 서버는 단일 프로세스라 탐지 작업 중에는 접수 응답(202)이 수 분 늦어질 수 있다.
     * 요청은 별도 스레드(aiRequestExecutor)에서 보내므로 길게 기다려도 앱 응답을 막지 않는다.
     */
    @Bean("aiRestTemplate")
    public RestTemplate aiRestTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofMinutes(5))
                .build();
    }
}
