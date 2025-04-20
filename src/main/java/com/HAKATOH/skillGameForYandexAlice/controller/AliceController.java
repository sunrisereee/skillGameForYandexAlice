package com.HAKATOH.skillGameForYandexAlice.controller;

import com.HAKATOH.skillGameForYandexAlice.entity.AliceSkill.request.AliceRequest;
import com.HAKATOH.skillGameForYandexAlice.entity.AliceSkill.response.AliceResponse;
import com.HAKATOH.skillGameForYandexAlice.entity.GameState;
import com.HAKATOH.skillGameForYandexAlice.service.GigaChatService;
import com.HAKATOH.skillGameForYandexAlice.service.GameStateService;
import com.HAKATOH.skillGameForYandexAlice.service.AliceResponseBuilderService;
import com.HAKATOH.skillGameForYandexAlice.service.GigaChatSessionCache;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

@AllArgsConstructor
@RestController
public class AliceController {

    private final AliceResponseBuilderService responseBuilder;
    private final GigaChatService gigachatService;
    private final GameStateService gameStateService;
    private final ObjectMapper objectMapper;
    private final GigaChatSessionCache gigaChatSessionCache;

    private static final String RULES_TEXT = """
        Привет, путник. Ты попал в игру под названием Петля. Вот основные правила:
        1. Ты проживаешь один и тот же день снова и снова
        2. Стандартные действия приведут к повторению дня
        3. Только неочевидные решения продвигают сюжет
        4. Экспериментируй и ищи закономерности
        
        Доступные команды:
        - "Правила" - показать это сообщение
        - "Начать заново" или "новая" - начать игру заново""";

    private static final String WELCOME_TEXT = RULES_TEXT + "\n\nЕсли готов, скажи \"Начать\"";

    @PostMapping("/game")
    public Mono<AliceResponse> handleAliceRequestGame(@RequestBody AliceRequest request) {
        String sessionId = request.getSession().getSession_id();
        String userId = request.getSession().getUser_id();
        String userMessage = request.getRequest().getCommand();
        GameState state = gameStateService.getState(userId);

        if(userMessage.matches("(?i)(правила|помощь|help|как играть)")) {
            return showRules();
        }

        if(shouldStartNewGame(userMessage, state)) {
            if(isGameInProgress(state)) {
                gameStateService.deleteState(userId);
                return responseBuilder.buildSimpleResponseAsync(
                        "Вы начали игру заново.\n\n" + WELCOME_TEXT,
                        false
                );
            }
            GameState newState = new GameState();
            newState.setUserId(userId);
            newState.setHistory(state.getHistory());
            gameStateService.saveState(newState);

            return startNewGame(userId, sessionId);
        }

        if(isGameInProgress(state)) {
            try {
                String selectedOption = parseUserChoice(userMessage, state);
                if (selectedOption != null) {
                    return continueGame(userId, selectedOption, state, sessionId);
                }
            } catch (Exception e) {
                System.err.println("Ошибка обработки выбора: " + e.getMessage());
            }
            return continueGame(userId, userMessage, state, sessionId);
        }

        return showRules();
    }

    private boolean shouldStartNewGame(String userMessage, GameState state) {
        return state.getHistory() == null || state.getHistory().isEmpty() ||
                userMessage.matches("(?i)(новая игра|старт|начать заново)");
    }

    private boolean isGameInProgress(GameState state) {
        return state != null && state.getHistory() != null && !state.getHistory().isEmpty();
    }

    private Mono<AliceResponse> showRules() {
        return responseBuilder.buildSimpleResponseAsync(WELCOME_TEXT, false);
    }

    private static final Pattern CHOICE_PATTERN = Pattern.compile(
            "^\\s*(\\d+|перв(ый|ая|ое)?|втор(ой|ая|ое)?|трет(ий|ья|ье)?|четверт(ый|ая|ое)?|пят(ый|ая|ое)?|один|два|три|четыре|пять)\\s*$",
            Pattern.CASE_INSENSITIVE
    );

    private String parseUserChoice(String userInput, GameState state) throws JsonProcessingException {

        // если ввод не похож на вариант - возвращаем как есть
        if (!CHOICE_PATTERN.matcher(userInput).matches()) {
            return userInput;
        }

        List<String> lastOptions = state.getLastOptionsHistory() != null ?
                objectMapper.readValue(state.getLastOptionsHistory(), List.class) :
                Collections.emptyList();

        if (lastOptions.isEmpty()) {
            return null;
        }

        int choiceIndex = getChoiceIndex(userInput);

        if (choiceIndex >= 0 && choiceIndex < lastOptions.size()) {
            return lastOptions.get(choiceIndex);
        }

        return null;
    }

    private static int getChoiceIndex(String userInput) {
        String cleanInput = userInput.toLowerCase().trim();

        int choiceIndex = -1;

        //обработка числа
        if (cleanInput.matches("\\d+")) {
            choiceIndex = Integer.parseInt(cleanInput) - 1;
        }

        //обработка текста
        else if (cleanInput.startsWith("перв") || cleanInput.equals("один")) choiceIndex = 0;
        else if (cleanInput.startsWith("втор") || cleanInput.equals("два")) choiceIndex = 1;
        else if (cleanInput.startsWith("трет") || cleanInput.equals("три")) choiceIndex = 2;
        else if (cleanInput.startsWith("четвер") || cleanInput.equals("четыре")) choiceIndex = 3;
        else if (cleanInput.startsWith("пят") || cleanInput.equals("пять")) choiceIndex = 4;
        return choiceIndex;
    }

    private Mono<AliceResponse> startNewGame(String userId, String sessionId) {
        return gigachatService.getInitialGameResponse()
                .flatMap(response -> {
                    System.out.println("Гигачат начинает игру" + response);

                    JsonNode responseNode = null;
                    try {
                        responseNode = objectMapper.readTree(response);

                        String firstSituation = responseNode.get("reduced_situation").asText();

                        //Добавляю варианты в бд
                        List<String> newOptions = new ArrayList<>();
                        responseNode.get("options").forEach(opt -> newOptions.add(opt.asText()));

                        ArrayNode history = objectMapper.createArrayNode();
                        history.add(firstSituation);

                        GameState newState = new GameState();
                        newState.setUserId(userId);
                        newState.setHistory(history.toString());
                        newState.setLastOptionsHistory(objectMapper.writeValueAsString(newOptions));
                        gameStateService.saveState(newState);

                        StringBuilder text = new StringBuilder(firstSituation)
                                .append("\n\nВарианты:\n");
                        for (int i = 0; i < newOptions.size(); i++) {
                            text.append(i + 1).append(". ").append(newOptions.get(i)).append("\n");
                        }

                        return responseBuilder.buildSimpleResponseAsync(text.toString(), false);
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                })
                .onErrorResume(e -> errorResponse("Не удалось начать игру"));
    }

    private Mono<AliceResponse> buildResponse(String jsonResponse, String userId, String sessionId) throws JsonProcessingException {
        try {
            JsonNode node = objectMapper.readTree(jsonResponse);
            StringBuilder text = new StringBuilder(node.get("situation").asText())
                    .append("\n\nВарианты:\n");

            JsonNode options = node.get("options");
            for (int i = 0; i < options.size(); i++) {
                text.append(i + 1).append(". ").append(options.get(i).asText()).append("\n");
            }

            boolean endSession = node.has("is_ended") && node.get("is_ended").asBoolean();

            if (endSession) {
                gameStateService.deleteState(userId);
                gigaChatSessionCache.clearSession(sessionId);
            }

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

    private Mono<AliceResponse> continueGame(String userId, String userAction, GameState state, String sessionId) {
        try {
            ArrayNode history = (ArrayNode) objectMapper.readTree(state.getHistory());
            boolean needReminder = state.getMessageCount() % 3 == 0;

            //Проверка на цикличность
            final boolean[] isCycling = {false};
            List<String> lastOptions = state.getLastOptionsHistory() != null ?
                    objectMapper.readValue(state.getLastOptionsHistory(), List.class) :
                    new ArrayList<>();

            //Хотя бы 3 варианта
            if (lastOptions.size() > 3) {
                long matches = lastOptions.stream()
                        .filter(opt -> Collections.frequency(lastOptions, opt) >= 2)
                        .distinct()
                        .count();
                isCycling[0] = matches >= 1; // Если хотя бы 1 вариант повторяется 2+ раза
                System.out.println("Зацикливание" + isCycling[0]);
            }


            return gigachatService.getGameResponse(state.getHistory(), userAction, sessionId, needReminder, isCycling[0])
                    .flatMap(response -> {
                        System.out.println("Гигачат продолжает" + response);
                        JsonNode newResponse;
                        try {
                            newResponse = objectMapper.readTree(response);

                            List<String> newOptions = new ArrayList<>();
                            newResponse.get("options").forEach(opt -> newOptions.add(opt.asText()));
                            List<String> updatedOptionsHistory = new ArrayList<>(lastOptions);
                            updatedOptionsHistory.addAll(newOptions);

                            //Оставляем только последние 9 вариантов
                            if (updatedOptionsHistory.size() > 9) {
                                updatedOptionsHistory = updatedOptionsHistory.subList(updatedOptionsHistory.size() - 9, updatedOptionsHistory.size());
                            }

                            if (isCycling[0]) {
                                updatedOptionsHistory.clear();
                                System.out.println("Сброс истории вариантов для игрока " + userId);
                            }

                            String newSituation = newResponse.get("reduced_situation").asText();
                            history.add(newSituation);

                            GameState updatedState = new GameState();
                            updatedState.setUserId(userId);
                            updatedState.setHistory(history.toString());
                            updatedState.setMessageCount(state.getMessageCount() + 1);
                            updatedState.setLastOptionsHistory(updatedOptionsHistory.isEmpty() ? null : objectMapper.writeValueAsString(updatedOptionsHistory));
                            gameStateService.saveState(updatedState);

                            return buildResponse(response, userId, sessionId);
                        } catch (JsonProcessingException e) {
                            throw new RuntimeException(e);
                        }
                    });
        } catch (Exception e) {
            return errorResponse("Ошибка в ходе игры");
        }
    }

}
