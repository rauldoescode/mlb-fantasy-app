package com.mlbfantasy.repository;

import com.mlbfantasy.model.PlayerScheduledGame;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayerScheduledGameRepository extends JpaRepository<PlayerScheduledGame, Long> {

    List<PlayerScheduledGame> findByPlayerIdAndGameDate(Integer playerId, LocalDate gameDate);
}
