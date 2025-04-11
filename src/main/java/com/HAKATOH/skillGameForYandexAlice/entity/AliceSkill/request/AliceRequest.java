package com.HAKATOH.skillGameForYandexAlice.entity.AliceSkill.request;

import lombok.Data;

@Data
public class AliceRequest {
    private Request request;
    private Session session;
    private String version;
}
