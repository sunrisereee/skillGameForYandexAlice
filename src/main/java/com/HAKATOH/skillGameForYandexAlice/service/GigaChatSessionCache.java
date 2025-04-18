package com.HAKATOH.skillGameForYandexAlice.service;

import chat.giga.model.completion.ChatMessage;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.HAKATOH.skillGameForYandexAlice.service.GigaChatService.SYSTEM_PROMPT;

@Service
public class GigaChatSessionCache {
    private final Map<String, ChatMessage> sessionSystemMessages = new ConcurrentHashMap<>();

    public ChatMessage getSystemMessage(String sessionId) {
        return sessionSystemMessages.computeIfAbsent(sessionId,
                id -> ChatMessage.builder()
                        .role(ChatMessage.Role.SYSTEM)
                        .content(SYSTEM_PROMPT)
                        .build());
    }

    public void clearSession(String sessionId) {
        sessionSystemMessages.remove(sessionId);
    }
}
