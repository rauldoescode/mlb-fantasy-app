package com.mlbfantasy.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "player_scheduled_games")
public class PlayerScheduledGame {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "player_id")
    private Integer playerId;

    @Column(name = "game_pk", nullable = false)
    private Integer gamePk;

    @Column(name = "game_date", nullable = false)
    private LocalDate gameDate;

    @Column(name = "game_start_time", nullable = false)
    private OffsetDateTime gameStartTime;

    protected PlayerScheduledGame() {
    }

    public Long getId() {
        return id;
    }

    public Integer getPlayerId() {
        return playerId;
    }

    public Integer getGamePk() {
        return gamePk;
    }

    public LocalDate getGameDate() {
        return gameDate;
    }

    public OffsetDateTime getGameStartTime() {
        return gameStartTime;
    }
}
