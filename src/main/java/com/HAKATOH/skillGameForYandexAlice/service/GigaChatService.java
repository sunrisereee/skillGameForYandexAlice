package com.HAKATOH.skillGameForYandexAlice.service;

import chat.giga.client.GigaChatClient;
import chat.giga.client.auth.AuthClient;
import chat.giga.client.auth.AuthClientBuilder;
import chat.giga.model.ModelName;
import chat.giga.model.Scope;
import chat.giga.model.completion.ChatMessage;
import chat.giga.model.completion.CompletionRequest;
import chat.giga.model.completion.CompletionResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    Ты — ИИ для текстовых квестов. Ты должен СТРОГО следовать этим правилам:
    
     1. Формат ответа - ТОЛЬКО JSON, без пояснений, без markdown, без лишних символов.
     2. Температура ответа установлена на 0.3 для стабильности.
     3. Если нарушишь формат - игра сломается.
    
     Структура ответа (повторяй её ТОЧНО каждый раз):
     {
         "situation": "1-2 предложения текущей ситуации",
         "options": ["вариант1 (до 10 слов)", "вариант2", "вариант3"],
         "last_situation": "краткое описание предыдущей ситуации или пустая строка",
         "last_action_user": "действие игрока или пустая строка",
         "is_ended": false/true
     }
            
     Жёсткие требования:
     1. is_ended=true ТОЛЬКО при:
        - Смерти без вариантов спасения
        - Достижении финала
        - Явном запросе "завершить игру"
     2. options - ВСЕГДА 3 варианта, даже если ситуация кажется тупиковой
     3. situation - максимум 2 предложения
     4. last_situation - сокращай до 10 слов если нужно
        
     Пример правильного ответа:
     {
         "situation": "Вы в тёмной комнате, слышите скрип двери",
         "options": ["Осмотреться", "Крикнуть о помощи", "Попытаться открыть дверь"],
         "last_situation": "Вы проснулись в незнакомом месте",
         "last_action_user": "открыть глаза",
         "is_ended": false
     }
    """;

    private static final String INITIAL_PROMPT = """
    Сгенерируй начало игры. Игрок проснулся в комнате, за окном ночь
    
    Требования к старту:
    1. Таинственная, но не пугающая атмосфера
    2. Намёк на возможную опасность
    3. Должен быть выход из комнаты
    """;

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


    //Просто запрос нейросети с ответом
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

    //Вопрос нейросети с системным промтом, задаем правила ответа
    public Mono<String> getRawJsonResponse(String userMessage) {
        return Mono.fromCallable(() -> {
            GigaChatClient client = createClient();

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

    //Убираем лишние символы, если они есть
    private String extractPureJson(String content) {
        return content.replaceAll("```json|```", "").trim();
    }


    //Вопрос нейросети с системным промтом, для игры
    public Mono<String> getGameResponse(String action, String situation, String historyJson) {
        return Mono.fromCallable(() -> {
            GigaChatClient client = createClient();


            String prompt = String.format("""
            История игры: %s
            Ситуация: %s
            Выбранное действие: %s
            Сгенерируй следующую ситуацию в игре согласно правилам.
            """, historyJson, situation, action);
            System.out.println("Запрос пользователя" + prompt);
            CompletionResponse response = client.completions(
                    CompletionRequest.builder()
                            .model(ModelName.GIGA_CHAT)
                            .temperature(0.3f)
                            .messages(List.of(
                                    ChatMessage.builder()
                                            .role(ChatMessage.Role.SYSTEM)
                                            .content(SYSTEM_PROMPT)
                                            .build(),
                                    ChatMessage.builder()
                                            .role(ChatMessage.Role.USER)
                                            .content(prompt)
                                            .build()))
                            .build());

            return extractPureJson(response.choices().get(0).message().content());
        });
    }


    public Mono<String> getInitialGameResponse() {
        return Mono.fromCallable(() -> {
            GigaChatClient client = createClient();
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
                                            .content(INITIAL_PROMPT)
                                            .build()))
                            .build());
            return extractPureJson(response.choices().get(0).message().content());
        });
    }
}