package com.mlbfantasy.repository;

import com.mlbfantasy.model.RosterSlot;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RosterSlotRepository extends JpaRepository<RosterSlot, UUID> {

    List<RosterSlot> findByLeagueIdAndUserId(UUID leagueId, UUID userId);

    List<RosterSlot> findByLeagueIdAndUserIdAndActiveTrue(UUID leagueId, UUID userId);

    Optional<RosterSlot> findByLeagueIdAndUserIdAndPlayerId(UUID leagueId, UUID userId, Integer playerId);

    long countByLeagueIdAndUserId(UUID leagueId, UUID userId);
}
