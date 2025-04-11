package com.HAKATOH.skillGameForYandexAlice.entity.AliceSkill.request;

import lombok.Data;

@Data
public class Session {
    private String message_id;
    private String session_id;
    private String skill_id;
    private String user_id;
    private boolean new_;
}
