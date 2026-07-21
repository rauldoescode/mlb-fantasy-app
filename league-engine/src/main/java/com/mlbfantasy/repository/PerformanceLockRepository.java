package com.mlbfantasy.repository;

import com.mlbfantasy.model.PerformanceLock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PerformanceLockRepository extends JpaRepository<PerformanceLock, UUID> {

    List<PerformanceLock> findByLeagueIdAndUserIdAndWeekNumber(
            UUID leagueId, UUID userId, int weekNumber);

    Optional<PerformanceLock> findByLeagueIdAndUserIdAndWeekNumberAndPlayerId(
            UUID leagueId, UUID userId, int weekNumber, Integer playerId);
}
