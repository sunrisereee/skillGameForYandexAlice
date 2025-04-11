package com.HAKATOH.skillGameForYandexAlice.entity.AliceSkill.response;

import lombok.Data;

@Data
public class Response {
    private String text;
    private String tts;
    private boolean end_session;
}
