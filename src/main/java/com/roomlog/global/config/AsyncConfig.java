package com.roomlog.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * 자가 수리 안내 미리 생성용 스레드 풀.
     * 하자 목록 조회 응답을 막지 않는 백그라운드 작업이라, 큐가 넘치면 조용히 버린다.
     * (버려져도 사용자가 안내를 열 때 그 자리에서 생성되므로 기능은 그대로 동작한다.)
     */
    @Bean("selfRepairExecutor")
    public Executor selfRepairExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(6);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("self-repair-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        executor.initialize();
        return executor;
    }

    /**
     * AI 서버 요청(재구성·하자 탐지·비교) 전송용 스레드 풀.
     * AI 서버가 탐지 중일 때는 접수 응답이 수 분 늦어질 수 있어 HTTP 요청 스레드에서 기다리지 않고 여기서 보낸다.
     * 버리면 분석이 영영 PENDING으로 남으므로 큐가 차면 호출 스레드에서 직접 보낸다(CallerRunsPolicy).
     */
    @Bean("aiRequestExecutor")
    public Executor aiRequestExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("ai-request-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
