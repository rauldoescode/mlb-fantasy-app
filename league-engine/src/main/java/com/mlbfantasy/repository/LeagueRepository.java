package com.mlbfantasy.repository;

import com.mlbfantasy.model.League;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface LeagueRepository extends JpaRepository<League, UUID> {

    @Query("""
            SELECT l FROM League l
            WHERE l.id IN (
                SELECT m.id.leagueId FROM LeagueMember m WHERE m.id.userId = :userId
            )
            """)
    List<League> findLeaguesForUser(UUID userId);
}
