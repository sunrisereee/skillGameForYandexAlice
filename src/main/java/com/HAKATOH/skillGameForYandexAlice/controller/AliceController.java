package com.HAKATOH.skillGameForYandexAlice.controller;

import com.HAKATOH.skillGameForYandexAlice.entity.AliceSkill.request.AliceRequest;
import com.HAKATOH.skillGameForYandexAlice.entity.AliceSkill.response.AliceResponse;
import com.HAKATOH.skillGameForYandexAlice.service.GigaChatService;
import com.HAKATOH.skillGameForYandexAlice.service.ResponseBuilderService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@AllArgsConstructor
@RestController
public class AliceController {

    private final ResponseBuilderService responseBuilder;
    private final GigaChatService gigachatService;

    //ЭХО
//    @PostMapping("/")
//    public AliceResponse handleAliceRequest(@RequestBody AliceRequest aliceRequest) {
//        return responseBuilder.buildSimpleResponse(aliceRequest.getRequest().getCommand(), false);
//    }

    @PostMapping("/")
    public Mono<AliceResponse> handleAliceRequest(@RequestBody AliceRequest aliceRequest) {
        String userMessage = aliceRequest.getRequest().getCommand();

        if (userMessage == null || userMessage.isEmpty()) {
            return Mono.just(responseBuilder.buildSimpleResponse(
                    "Привет! Я могу ответить на твой вопрос. Спроси меня о чем-нибудь.",
                    false
            ));
        }

        return gigachatService.getGigaChatResponse(userMessage)
                .map(response -> responseBuilder.buildSimpleResponse(response, false))
                .onErrorResume(e -> Mono.just(responseBuilder.buildSimpleResponse(
                        "Извините, не удалось получить ответ. Попробуйте позже.",
                        false
                )));
    }
}
