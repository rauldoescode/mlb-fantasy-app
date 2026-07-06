package com.mlbfantasy.repository;

import com.mlbfantasy.model.DailyPerformance;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyPerformanceRepository extends JpaRepository<DailyPerformance, Long> {

    List<DailyPerformance> findByPlayerIdInAndGameDateBetween(
            Collection<Integer> playerIds, LocalDate start, LocalDate end);

    List<DailyPerformance> findByPlayerIdOrderByGameDateDesc(Integer playerId);
}
