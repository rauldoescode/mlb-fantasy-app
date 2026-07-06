package com.mlbfantasy.repository;

import com.mlbfantasy.model.Player;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlayerRepository extends JpaRepository<Player, Integer> {

    @Query("""
            SELECT p FROM Player p
            WHERE (:search IS NULL OR LOWER(p.fullName) LIKE LOWER(CONCAT('%', :search, '%')))
              AND (:position IS NULL OR p.primaryPos = :position)
              AND (:team IS NULL OR p.teamAbbrev = :team)
            """)
    Page<Player> search(@Param("search") String search,
                        @Param("position") String position,
                        @Param("team") String team,
                        Pageable pageable);
}
