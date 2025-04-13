package com.HAKATOH.skillGameForYandexAlice.repository;

import com.HAKATOH.skillGameForYandexAlice.entity.GameState;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameStateRepository extends JpaRepository<GameState, String> {
}
