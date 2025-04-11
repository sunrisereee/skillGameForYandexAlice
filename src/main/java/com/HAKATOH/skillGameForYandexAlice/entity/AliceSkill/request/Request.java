package com.HAKATOH.skillGameForYandexAlice.entity.AliceSkill.request;

import lombok.Data;

@Data
public class Request {
    private String command;
    private String original_utterance;
    private String type;
    private Nlu nlu;
}
