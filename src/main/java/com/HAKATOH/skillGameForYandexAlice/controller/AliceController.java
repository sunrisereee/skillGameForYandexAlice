package com.HAKATOH.skillGameForYandexAlice.controller;

import com.HAKATOH.skillGameForYandexAlice.entity.AliceSkill.request.AliceRequest;
import com.HAKATOH.skillGameForYandexAlice.entity.AliceSkill.response.AliceResponse;
import com.HAKATOH.skillGameForYandexAlice.entity.GameState;
import com.HAKATOH.skillGameForYandexAlice.service.GigaChatService;
import com.HAKATOH.skillGameForYandexAlice.service.GameStateService;
import com.HAKATOH.skillGameForYandexAlice.service.AliceResponseBuilderService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

@AllArgsConstructor
@RestController
public class AliceController {

    private final AliceResponseBuilderService responseBuilder;
    private final GigaChatService gigachatService;
    private final GameStateService gameStateService;
    private final ObjectMapper objectMapper;


    @PostMapping("/")
    public Mono<AliceResponse> handleAliceRequest(@RequestBody AliceRequest aliceRequest) {
        String userMessage = aliceRequest.getRequest().getCommand();

        if (userMessage == null || userMessage.isEmpty()) {
            return Mono.just(responseBuilder.buildSimpleResponse(
                    "Привет! Я могу ответить на твой вопрос в строгом формате JSON",
                    false
            ));
        }
        return  gigachatService.getGigaChatResponse(userMessage)
                .map(response -> responseBuilder.buildSimpleResponse(response, false))
                .onErrorResume(e -> Mono.just(responseBuilder.buildSimpleResponse(
                       "Извините, не удалось получить ответ. Попробуйте позже.",
                       false
            )));
    }

    @PostMapping("/game")
    public Mono<AliceResponse> handleAliceRequestGame(@RequestBody AliceRequest request) {
        String userId = request.getSession().getUser_id();
        String userMessage = request.getRequest().getCommand();
        GameState state = gameStateService.getState(userId);

        if (shouldStartNewGame(userMessage, state)) {
            return startNewGame(userId);
        }

        return continueGame(userId, userMessage, state);
    }

    private boolean shouldStartNewGame(String userMessage, GameState state) {
        return state.getHistory() == null || state.getHistory().isEmpty() ||
                userMessage.matches("(?i)(начать|новая|старт)");
    }

    private Mono<AliceResponse> startNewGame(String userId) {
        return gigachatService.getInitialGameResponse()
                .flatMap(response -> {
                    System.out.println("Гигачат начиниет игру" + response);
                    JsonNode responseNode = null;
                    try {
                        responseNode = objectMapper.readTree(response);
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                    String firstSituation = responseNode.get("situation").asText();

                    ArrayNode history = objectMapper.createArrayNode();
                    ObjectNode firstEntry = objectMapper.createObjectNode();
                    firstEntry.put("action", "");
                    firstEntry.put("situation", firstSituation);
                    history.add(firstEntry);

                    GameState newState = new GameState();
                    newState.setUserId(userId);
                    newState.setHistory(history.toString());
                    gameStateService.saveState(newState);
                    try {
                        return buildResponse(response, userId);
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                })
                .onErrorResume(e -> errorResponse("Не удалось начать игру"));
    }

    private Mono<AliceResponse> buildResponse(String jsonResponse, String userId) throws JsonProcessingException {
        try {
            JsonNode node = objectMapper.readTree(jsonResponse);
            StringBuilder text = new StringBuilder(node.get("situation").asText())
                    .append("\n\nВарианты:\n");

            JsonNode options = node.get("options");
            for (int i = 0; i < options.size(); i++) {
                text.append(i + 1).append(". ").append(options.get(i).asText()).append("\n");
            }

            boolean endSession = node.has("is_ended") && node.get("is_ended").asBoolean();

            if (endSession) gameStateService.deleteState(userId);

            return responseBuilder.buildSimpleResponseAsync(text.toString(), endSession);
        } catch (JsonProcessingException e) {
            return Mono.error(new RuntimeException("Ошибка парсинга JSON ответа"));
        } catch (Exception e) {
            return Mono.error(new RuntimeException("Неизвестная ошибка при формировании ответа"));
        }
    }

    private Mono<AliceResponse> errorResponse(String message) {
        return responseBuilder.buildSimpleResponseAsync(message, false);
    }

    private Mono<AliceResponse> continueGame(String userId, String userAction, GameState state) {
        try {
            // Парсим историю
            ArrayNode history = (ArrayNode) objectMapper.readTree(state.getHistory());

            // Получаем последнюю ситуацию
            JsonNode lastEntry = history.get(history.size() - 1);
            String lastSituation = lastEntry.get("situation").asText();

            // Получаем новый ответ от GigaChat
            return gigachatService.getGameResponse(userAction, lastSituation, state.getHistory())
                    .flatMap(response -> {
                        System.out.println("Гигачат продолжает" + response);
                        JsonNode newResponse;
                        try {
                            newResponse = objectMapper.readTree(response);
                        } catch (JsonProcessingException e) {
                            throw new RuntimeException(e);
                        }
                        ((ObjectNode)lastEntry).put("action", userAction);

                        String newSituation = newResponse.get("situation").asText();

                        // Обновляем историю
                        ObjectNode newEntry = objectMapper.createObjectNode();
                        newEntry.put("situation", newSituation);
                        newEntry.put("action", "");
                        history.add(newEntry);

                        // Сохраняем только связки ситуация-действие
                        GameState updatedState = new GameState();
                        updatedState.setUserId(userId);
                        updatedState.setHistory(history.toString());
                        gameStateService.saveState(updatedState);

                        // Формируем ответ пользователю
                        try {
                            return buildResponse(response, userId);
                        } catch (JsonProcessingException e) {
                            throw new RuntimeException(e);
                        }
                    });
        } catch (Exception e) {
            return errorResponse("Ошибка в ходе игры");
        }
    }

}
