package com.HAKATOH.skillGameForYandexAlice.service;

import com.HAKATOH.skillGameForYandexAlice.entity.AliceSkill.response.AliceResponse;
import com.HAKATOH.skillGameForYandexAlice.entity.AliceSkill.response.Response;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class AliceResponseBuilderService {
    public AliceResponse buildSimpleResponse(String text, boolean endSession) {
        Response responsePayload = createResponsePayload(text, text, endSession);
        return createAliceResponse(responsePayload);
    }


    private Response createResponsePayload(String text, String tts, boolean endSession) {
        Response response = new Response();
        response.setText(text);
        response.setTts(tts);
        response.setEnd_session(endSession);
        return response;
    }

    private AliceResponse createAliceResponse(Response responsePayload) {
        AliceResponse response = new AliceResponse();
        response.setResponse(responsePayload);
        response.setVersion("1.0");
        return response;
    }

    public Mono<AliceResponse> buildSimpleResponseAsync(String text, boolean endSession) {
        return Mono.fromCallable(() -> buildSimpleResponse(text, endSession));
    }
}
