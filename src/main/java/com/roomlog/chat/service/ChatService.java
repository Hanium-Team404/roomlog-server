package com.roomlog.chat.service;

import com.roomlog.chat.domain.AppGuide;
import com.roomlog.chat.domain.ChatAnswerCache;
import com.roomlog.chat.domain.ChatMessage;
import com.roomlog.chat.domain.ChatSession;
import com.roomlog.chat.dto.CreateChatSessionResponse;
import com.roomlog.chat.dto.GetChatMessagesResponse;
import com.roomlog.chat.dto.SendChatMessageRequest;
import com.roomlog.chat.dto.SendChatMessageResponse;
import com.roomlog.chat.repository.ChatAnswerCacheRepository;
import com.roomlog.chat.repository.ChatMessageRepository;
import com.roomlog.chat.repository.ChatSessionRepository;
import com.roomlog.defect.dto.DefectChatContext;
import com.roomlog.defect.service.DefectService;
import com.roomlog.global.exception.CustomException;
import com.roomlog.global.exception.ErrorCode;
import com.roomlog.global.infra.GptClient;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 앱 사용법 안내 챗봇.
 * 답변은 아래 순서로 정해진다. 1~2번에서 끝나면 GPT를 호출하지 않는다.
 * 1) 추천 질문 버튼 → 고정 안내문 그대로 반환
 * 2) 같은 질문 캐시 적중 → 저장된 답변 재사용
 * 3) 관련 섹션이 잡히면 그 섹션들 + 최근 대화 4턴으로 GPT 호출
 * 4) 키워드가 하나도 안 걸리면 안내 전문을 넣고 GPT가 판단하게 한다(source는 FALLBACK).
 *
 * defectId가 실려 오면 위 흐름을 타지 않고 하자 상담으로 분기한다.
 * 하자마다 답이 달라야 하므로 안내 섹션 매칭도, 답변 캐시도 쓰지 않는다.
 */
@Service
@RequiredArgsConstructor
public class ChatService {

    private static final String GREETING =
            "안녕하세요! 룸로그 사용법을 안내해드릴게요. 궁금한 점을 입력하시거나 아래 질문을 눌러보세요.";

    /** GPT에 실어 보낼 관련 안내 섹션 수. 늘릴수록 정확도는 오르고 토큰은 늘어난다. */
    private static final int GUIDE_CONTEXT_LIMIT = 4;
    /** GPT에 실어 보낼 직전 메시지 수(4턴). */
    private static final int HISTORY_LIMIT = 8;
    /** 직전 메시지를 잘라 보낼 길이. */
    private static final int HISTORY_CONTENT_LIMIT = 400;
    /** 캐시 키 최대 길이. ChatAnswerCache.questionKey 컬럼 길이와 맞춘다. */
    private static final int CACHE_KEY_LIMIT = 200;

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatAnswerCacheRepository chatAnswerCacheRepository;
    private final DefectService defectService;
    private final GptClient gptClient;

    @Transactional
    public CreateChatSessionResponse createSession(Long userId) {
        ChatSession session = chatSessionRepository.save(ChatSession.builder().userId(userId).build());
        return CreateChatSessionResponse.of(session, GREETING);
    }

    /**
     * GPT 호출이 1~3초 걸리므로 트랜잭션을 걸지 않는다.
     * 트랜잭션 안에서 호출하면 그동안 DB 커넥션을 붙잡아, 동시 사용자가 늘면 풀이 말라 다른 API까지 막힌다.
     * 각 저장은 리포지토리 단위 트랜잭션으로 처리하고, 질문과 답변을 답변 생성 뒤 함께 저장해
     * 실패했을 때 답변 없는 질문만 이력에 남는 일이 없게 한다.
     */
    public SendChatMessageResponse sendMessage(Long userId, Long sessionId, SendChatMessageRequest request) {
        ChatSession session = findOwnedSession(userId, sessionId);

        List<ChatMessage> history = recentHistory(session.getId());
        String question = request.getMessage().trim();
        Long defectId = request.getDefectId();

        // 하자를 골라 보낸 경우, 소유 확인은 GPT를 부르기 전에 끝낸다.
        Answer answer = defectId != null
                ? answerAboutDefect(userId, defectId, question, history)
                : resolveAnswer(question, request.getGuide(), history);

        save(session.getId(), ChatMessage.Role.USER, question, defectId);
        ChatMessage saved = save(session.getId(), ChatMessage.Role.ASSISTANT, answer.content(), defectId);

        return SendChatMessageResponse.of(saved, answer.source());
    }

    @Transactional(readOnly = true)
    public GetChatMessagesResponse getMessages(Long userId, Long sessionId) {
        ChatSession session = findOwnedSession(userId, sessionId);
        return GetChatMessagesResponse.of(session.getId(),
                chatMessageRepository.findBySessionIdOrderByIdAsc(session.getId()));
    }

    /**
     * 사용자가 고른 하자를 근거로 답한다.
     * 남의 집·대표 집 밖의 하자면 DefectService가 여기서 예외를 던져 GPT를 호출하지 않는다.
     */
    private Answer answerAboutDefect(Long userId, Long defectId, String question, List<ChatMessage> history) {
        DefectChatContext defect = defectService.getChatContext(userId, defectId);

        String generated = gptClient.answerDefectQuestion(
                question, defectContext(defect), toGptMessages(history));

        return new Answer(generated, SendChatMessageResponse.Source.GPT);
    }

    private String defectContext(DefectChatContext defect) {
        return """
                하자 종류: %s
                심각도: %s
                위치: %s (%s)
                면적: %s㎠
                탐지 설명: %s
                자가 수리 가능 여부(확정): %s
                """.formatted(
                defect.type(),
                defect.severity(),
                defect.roomName(),
                defect.location(),
                defect.area() != null ? defect.area() : "미상",
                defect.description() != null && !defect.description().isBlank() ? defect.description() : "없음",
                defect.selfRepairPossible() ? "가능" : "불가능");
    }

    private Answer resolveAnswer(String question, String guideCode, List<ChatMessage> history) {
        AppGuide selected = parseGuide(guideCode);
        if (selected != null) {
            return new Answer(selected.getContent(), SendChatMessageResponse.Source.GUIDE);
        }

        String normalized = normalize(question);
        // 이전 대화가 있으면 같은 문장이라도 의미가 달라질 수 있어 캐시를 쓰지 않는다.
        boolean cacheable = history.isEmpty() && !normalized.isBlank();

        if (cacheable) {
            ChatAnswerCache cached = chatAnswerCacheRepository.findById(normalized).orElse(null);
            if (cached != null) {
                cached.hit();
                // 트랜잭션 밖이라 변경 감지가 동작하지 않는다. 조회 수는 명시적으로 저장한다.
                chatAnswerCacheRepository.save(cached);
                return new Answer(cached.getAnswer(), SendChatMessageResponse.Source.CACHE);
            }
        }

        List<AppGuide> matched = AppGuide.match(normalized, previousQuestions(history), GUIDE_CONTEXT_LIMIT);
        // 키워드가 안 걸려도 표현만 다른 질문일 수 있어, 안내 전문을 넣고 GPT가 답할 수 있는지 판단하게 한다.
        boolean offTopic = matched.isEmpty();
        List<AppGuide> context = offTopic ? AppGuide.all() : matched;

        String generated = gptClient.answerAppGuide(question, guideContext(context), toGptMessages(history));

        // 안내 범위 밖 답변은 캐시하지 않는다. 캐시로 돌려주면 source가 CACHE가 되어
        // 추천 질문이 함께 내려가지 않고, 나중에 안내를 보강해도 옛 거절 답변이 남는다.
        if (cacheable && !offTopic) {
            chatAnswerCacheRepository.save(ChatAnswerCache.builder()
                    .questionKey(normalized)
                    .answer(generated)
                    .build());
        }

        return new Answer(generated, offTopic
                ? SendChatMessageResponse.Source.FALLBACK
                : SendChatMessageResponse.Source.GPT);
    }

    /** 직전 사용자 질문을 이어붙여 정규화한다. 후속 질문의 주제를 추정하는 데 쓴다. */
    private String previousQuestions(List<ChatMessage> history) {
        return normalize(history.stream()
                .filter(message -> message.getRole() == ChatMessage.Role.USER)
                .map(ChatMessage::getContent)
                .reduce((a, b) -> a + " " + b)
                .orElse(""));
    }

    private AppGuide parseGuide(String guideCode) {
        if (guideCode == null || guideCode.isBlank()) return null;
        try {
            return AppGuide.valueOf(guideCode.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String guideContext(List<AppGuide> guides) {
        return guides.stream()
                .map(guide -> "■ " + guide.getTitle() + "\n" + guide.getContent())
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
    }

    private List<Map<String, String>> toGptMessages(List<ChatMessage> history) {
        List<Map<String, String>> messages = new ArrayList<>();
        for (ChatMessage message : history) {
            messages.add(Map.of(
                    "role", message.getRole() == ChatMessage.Role.USER ? "user" : "assistant",
                    "content", truncate(message.getContent(), HISTORY_CONTENT_LIMIT)));
        }
        return messages;
    }

    /** 최근 메시지를 오래된 순으로 반환한다. */
    private List<ChatMessage> recentHistory(Long sessionId) {
        List<ChatMessage> recent = new ArrayList<>(chatMessageRepository.findBySessionIdOrderByIdDesc(
                sessionId, PageRequest.of(0, HISTORY_LIMIT)));
        java.util.Collections.reverse(recent);
        return recent;
    }

    private ChatMessage save(Long sessionId, ChatMessage.Role role, String content, Long defectId) {
        return chatMessageRepository.save(ChatMessage.builder()
                .sessionId(sessionId)
                .role(role)
                .content(content)
                .defectId(defectId)
                .build());
    }

    private ChatSession findOwnedSession(Long userId, Long sessionId) {
        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new CustomException(ErrorCode.CHAT_001));

        if (!session.getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.CHAT_002);
        }
        return session;
    }

    /** 캐시 키용 정규화. 공백·문장부호를 없애 "스캔 어떻게 해요?"와 "스캔어떻게해요"를 같은 질문으로 본다. */
    private String normalize(String question) {
        String normalized = question.toLowerCase().replaceAll("[^0-9a-z가-힣]", "");
        return truncate(normalized, CACHE_KEY_LIMIT);
    }

    private String truncate(String text, int limit) {
        return text.length() <= limit ? text : text.substring(0, limit);
    }

    private record Answer(String content, SendChatMessageResponse.Source source) {
    }
}
