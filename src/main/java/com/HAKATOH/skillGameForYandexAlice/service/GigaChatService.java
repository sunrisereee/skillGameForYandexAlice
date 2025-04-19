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

    private final GigaChatSessionCache sessionCache;

    @Value("${gigachat.api.token}")
    private String authKey;

    static final String SYSTEM_PROMPT = """
    Ты — ИИ для текстовых квестов. Ты должен СТРОГО следовать этим правилам:
                                      
    1. Формат ответа - ТОЛЬКО JSON, без пояснений, без markdown, без лишних символов.
    2. Если нарушишь формат - игра сломается.
    3. Если ситуация и действия повторятся - игра сломается.
  
    Структура ответа (повторяй её ТОЧНО каждый раз):
    {
        "situation": "1-2 предложения текущей ситуации",
        "options": ["вариант1 (до 10 слов)", "вариант2", "вариант3"],
        "reduced_situation": "Текущая ситуация и выбор пользователя, в сокращенной форме, сократи до короткого предложения, сохранив смысл ситуации и выбор пользователя."
        "is_ended": false/true
    }
         
    Жёсткие требования:
    1. is_ended=true ТОЛЬКО при:
       - Смерти без вариантов спасения
       - Достижении финала
       - Явном запросе "завершить игру"
    2. options - ВСЕГДА 3 варианта, даже если ситуация кажется тупиковой, НИ В КОЕМ СЛУЧАЕ
        - НЕ ДОЛЖНЫ СОДЕРЖАТЬ ВАРИАНТЫ БЕЗ ДЕЙСТВИЯ, В ВАРИАНТАХ ВСЕГДА ДЕЙСТВИЕ
    3. situation - максимум 2 предложения
    4. situation НИ В КОЕМ СЛУЧАЕ НЕ ДОЛЖНЫ СОДЕРЖАТЬ ВАРИНАНТЫ ДЕЙСТВИЙ
    """;

    private static final String INITIAL_PROMPT = """
    Представь что ты оказался в темной комнате, за окном ночь.
       
    Требования к старту:
    1. Таинственная, но не пугающая атмосфера
    2. Намёк на возможную опасность
    3. Должен быть выход из комнаты
    """;

    private static final String REMAINDER_PROMT = """
    [НАПОМИНАНИЕ]
    ОТВЕТ СТРОГО В ФОРМАТЕ:
    {
        "situation": "1-2 предложения текущей ситуации",
        "options": ["вариант1 (до 10 слов)", "вариант2", "вариант3"],
        "reduced_situation": "Текущая ситуация и выбор пользователя, в сокращенной форме, сократи до короткого предложения, сохранив смысл ситуации и выбор пользователя."
        "is_ended": false/true
    }
    В options ВАРИНАТЫ ДЕЙСТВИЯ ПОЛЬЗОВАТЕЛЯ
    СИТУАЦИЯ НИ В КОЕМ СЛУЧАЕ НЕ ДОЛЖНА СОДЕРЖАТЬ ВАРИНАНТЫ ДЕЙСТВИЯ
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


    //Убираем лишние символы, если они есть
    private String extractPureJson(String content) {
        return content.replaceAll("```json|```", "").trim();
    }

    // РАБОЧИЙ ВАРИАНТ, НО ВЫСОКОЕ ПОТРЕБЛЕНИЕ ТОКЕНОВ
//    //Вопрос нейросети с системным промтом, для игры, без напоминания
//    public Mono<String> getGameResponse(String historyJson, String actionUser) {
//        return Mono.fromCallable(() -> {
//            GigaChatClient client = createClient();
//
//
//            String prompt = String.format("""
//            Контекст игры: %s
//            Выбор пользователя: %s
//            Сгенерируй следующую ситуацию в игре согласно правилам и учитывая выбор пользователя.
//            """, historyJson, actionUser);
//            System.out.println("Запрос пользователя" + prompt);
//            CompletionResponse response = client.completions(
//                    CompletionRequest.builder()
//                            .model(ModelName.GIGA_CHAT)
//                            .temperature(0.3f)
//                            .messages(List.of(
//                                    ChatMessage.builder()
//                                            .role(ChatMessage.Role.SYSTEM)
//                                            .content(SYSTEM_PROMPT)
//                                            .build(),
//                                    ChatMessage.builder()
//                                            .role(ChatMessage.Role.USER)
//                                            .content(prompt)
//                                            .build()))
//                            .build());
//
//            return extractPureJson(response.choices().get(0).message().content());
//        });
//    }


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


    //
     // ДАЛЬШЕ ТЕСТОВАЯ РЕАЛИЗАЦИЯ КЭШИРОВАНИЯ И НАПОМИНАНИЯ
     //

    //Отправка ответа с напоминаем правил
    public Mono<String> getGameResponse(String historyJson, String actionUser, String sessionId, Boolean reminder, Boolean isCicling) {
        return Mono.fromCallable(() -> {
            ChatMessage systemMessage = sessionCache.getSystemMessage(sessionId);

            ChatMessage finalSystemMessage = reminder ?
                    addMessageToPrompt(systemMessage, REMAINDER_PROMT) :
                    systemMessage;

            ChatMessage finalVersionSystemMessage = isCicling ?
                    addMessageToPrompt(finalSystemMessage, """
                            Ты зациклился, такая ситуация уже была. СРОЧНО придумай УНИКАЛЬНУЮ ситуацию или игра СЛОМАЕТСЯ.
                            СРОЧНО ПРИДУМАЙ НОВЫЕ ДЕЙСТВИЯ ДЛЯ ПОЛЬЗОВАТЕЛЯ""") :
                    finalSystemMessage;



            String prompt = String.format("""
            Контекст:%s
            Выбор:%s
            Сгенерируй следующую ситуацию в игре согласно правилам и учитывая выбор пользователя. Помни JSON ответ!
            """, historyJson, actionUser);

            ChatMessage userMessage = ChatMessage.builder()
                    .role(ChatMessage.Role.USER)
                    .content(prompt)
                    .build();

            CompletionResponse response = createClient().completions(
                    buildRequest(List.of(finalVersionSystemMessage, userMessage),
                            sessionId
                    ));

            return extractPureJson(response.choices().get(0).message().content());
        });
    }


    private ChatMessage addMessageToPrompt(ChatMessage original, String prompt) {
        return ChatMessage.builder()
                .role(original.role())
                .content(original.content() + prompt)
                .build();
    }

    private CompletionRequest buildRequest(List<ChatMessage> messages, String sessionId) {
        return CompletionRequest.builder()
                .model(ModelName.GIGA_CHAT)
                .temperature(0.3f)
                .messages(messages)
                .build();
    }

}