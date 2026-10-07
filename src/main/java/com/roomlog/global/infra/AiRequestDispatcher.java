package com.roomlog.global.infra;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * AI 서버 요청을 HTTP 요청 스레드 밖에서 보낸다.
 * 앱은 PENDING 응답을 받고 상태를 폴링하므로, AI 접수가 늦거나 실패해도 앱 요청은 바로 끝난다.
 * 실패하면 onFailure(보통 FAILED 표시)를 실행해 폴링 중인 앱이 실패를 알 수 있게 한다.
 */
@Slf4j
@Component
public class AiRequestDispatcher {

    @Async("aiRequestExecutor")
    public void dispatch(String label, Runnable request, Runnable onFailure) {
        try {
            request.run();
            log.info("AI 요청 접수 완료 - {}", label);
        } catch (Exception e) {
            log.error("AI 요청 실패 - {}, error: {}", label, e.getMessage(), e);
            try {
                onFailure.run();
            } catch (Exception inner) {
                log.error("AI 요청 실패 처리 중 오류 - {}, error: {}", label, inner.getMessage(), inner);
            }
        }
    }
}
