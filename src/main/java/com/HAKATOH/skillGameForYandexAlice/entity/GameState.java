package com.HAKATOH.skillGameForYandexAlice.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.Map;

@Data
@Entity
@Table(name = "game_states_new")
public class GameState {
    @Id
    @Column(name = "user_id")
    private String userId;

    @Column(name = "game_history", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String history;
}
