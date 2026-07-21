package com.mlbfantasy.repository;

import com.mlbfantasy.model.MatchupPlayerScore;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchupPlayerScoreRepository extends JpaRepository<MatchupPlayerScore, UUID> {

    List<MatchupPlayerScore> findByMatchupId(UUID matchupId);

    List<MatchupPlayerScore> findByMatchupIdAndUserId(UUID matchupId, UUID userId);

    void deleteByMatchupId(UUID matchupId);

    boolean existsByMatchupId(UUID matchupId);
}
