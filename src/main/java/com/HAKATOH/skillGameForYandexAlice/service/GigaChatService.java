package com.HAKATOH.skillGameForYandexAlice.service;

import chat.giga.client.GigaChatClient;
import chat.giga.client.auth.AuthClient;
import chat.giga.client.auth.AuthClientBuilder;
import chat.giga.model.ModelName;
import chat.giga.model.Scope;
import chat.giga.model.completion.ChatMessage;
import chat.giga.model.completion.CompletionRequest;
import chat.giga.model.completion.CompletionResponse;
import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GigaChatService {

    @Value("${gigachat.api.token}")
    private String authKey;

    private static final String SYSTEM_PROMPT = """
        Ты - JSON-ассистент. Все твои ответы должны быть строго в формате JSON.
        Отвечай только JSON-объектом, без пояснений и Markdown-разметки. Пользователь задает вопрос, ты отвечаешь четко в JSON формате.
        Theme Question - тема вопроса, опиши одним словом.
        AnswerX - варианты ответа не более 10 слов.
        Все поля JSON должны быть строго заполнены.
        Если вопроса от пользователя нет, просто сообщение также отвечаешь в формате JSON.
        
        Твоя четкая структура:
        {
            "Question" : "",
            "Theme Question": "",
            "Answer" : [
                {"Answer1" : ""},
                {"Answer2" : ""},
                {"Answer3" : ""}
            ],
            
        }
        """;

    public Mono<String> getGigaChatResponse(String userMessage) {
        return Mono.fromCallable(() -> {
            GigaChatClient client = createClient();

            CompletionResponse response = client.completions(
                    CompletionRequest.builder()
                            .model(ModelName.GIGA_CHAT)
                            .message(ChatMessage.builder()
                                    .content(userMessage)
                                    .role(ChatMessage.Role.USER)
                                    .build())
                            .build());

            return response.choices().get(0).message().content();
        });
    }

    public Mono<String> getRawJsonResponse(String userMessage) {
        return Mono.fromCallable(() -> {
            GigaChatClient client = createClient();

            // Формируем запрос с явным указанием в промпте необходимости JSON
            CompletionResponse response = client.completions(
                    CompletionRequest.builder()
                            .model(ModelName.GIGA_CHAT)
                            .messages(List.of(
                                    ChatMessage.builder()
                                            .role(ChatMessage.Role.SYSTEM)
                                            .content(SYSTEM_PROMPT)
                                            .build(),
                                    ChatMessage.builder()
                                            .role(ChatMessage.Role.USER)
                                            .content(userMessage)
                                            .build()))
                            .build());

            return extractPureJson(response.choices().get(0).message().content());
        });
    }

    private String extractPureJson(String content) {
        return content.replaceAll("```json|```", "").trim();
    }

    private GigaChatClient createClient() {
        return GigaChatClient.builder()
                .authClient(AuthClient.builder()
                        .withOAuth(AuthClientBuilder.OAuthBuilder.builder()
                                .scope(Scope.GIGACHAT_API_PERS)
                                .authKey(authKey)
                                .build())
                        .build())
                .build();
    }
}