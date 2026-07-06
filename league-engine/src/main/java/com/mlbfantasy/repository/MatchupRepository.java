package com.mlbfantasy.repository;

import com.mlbfantasy.model.Matchup;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MatchupRepository extends JpaRepository<Matchup, UUID> {

    List<Matchup> findByLeagueId(UUID leagueId);

    List<Matchup> findByLeagueIdAndWeekNumber(UUID leagueId, int weekNumber);

    @Query("""
            SELECT m FROM Matchup m
            WHERE m.leagueId = :leagueId AND m.weekNumber = :week
              AND (m.userOneId = :userId OR m.userTwoId = :userId)
            """)
    Optional<Matchup> findForUserInWeek(UUID leagueId, int week, UUID userId);

    List<Matchup> findByLeagueIdAndStatus(UUID leagueId, String status);
}
