package com.HAKATOH.skillGameForYandexAlice.service;

import com.HAKATOH.skillGameForYandexAlice.entity.GameState;
import com.HAKATOH.skillGameForYandexAlice.repository.GameStateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GameStateService {
    private final GameStateRepository repository;

    public void saveState(String userId, String history, String flags) {
        GameState state = repository.findById(userId)
                .orElse(new GameState());

        state.setUserId(userId);
        state.setHistory(history);

        repository.save(state);
    }

    public void saveState(GameState state) {
        repository.save(state);
    }

    public GameState getState(String userId) {
        return repository.findById(userId).orElse(new GameState());
    }


    public void deleteState(String userId) {
        repository.deleteById(userId);
    }

}
