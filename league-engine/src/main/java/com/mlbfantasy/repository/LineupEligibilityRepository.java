package com.mlbfantasy.repository;

import com.mlbfantasy.model.LineupEligibility;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LineupEligibilityRepository extends JpaRepository<LineupEligibility, UUID> {

    List<LineupEligibility> findByLeagueIdAndUserIdAndWeekNumber(
            UUID leagueId, UUID userId, int weekNumber);

    Optional<LineupEligibility> findByLeagueIdAndUserIdAndWeekNumberAndPlayerIdAndGameDate(
            UUID leagueId, UUID userId, int weekNumber, Integer playerId, LocalDate gameDate);
}
