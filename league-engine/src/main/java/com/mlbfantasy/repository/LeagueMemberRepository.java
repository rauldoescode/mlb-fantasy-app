package com.mlbfantasy.repository;

import com.mlbfantasy.model.LeagueMember;
import com.mlbfantasy.model.LeagueMemberId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeagueMemberRepository extends JpaRepository<LeagueMember, LeagueMemberId> {

    List<LeagueMember> findByIdLeagueId(UUID leagueId);

    List<LeagueMember> findByIdUserId(UUID userId);

    boolean existsByIdLeagueIdAndIdUserId(UUID leagueId, UUID userId);

    long countByIdLeagueId(UUID leagueId);
}
