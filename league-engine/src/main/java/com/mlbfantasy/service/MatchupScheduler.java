package com.mlbfantasy.service;

import com.mlbfantasy.model.League;
import com.mlbfantasy.repository.LeagueRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Automatically keeps every league's H2H schedule up to date and finalizes weeks
 * after they end so scores become immutable.
 *
 * <p>Generate runs daily; finalize runs Tuesday 06:00 in the league zone (after
 * Monday night West Coast games and the nightly stat sync window). The read path
 * also lazily fills missing weeks, so a missed generate run self-heals on next load.
 */
@Component
public class MatchupScheduler {

    private static final Logger log = LoggerFactory.getLogger(MatchupScheduler.class);

    private final LeagueRepository leagueRepository;
    private final MatchupService matchupService;

    public MatchupScheduler(LeagueRepository leagueRepository, MatchupService matchupService) {
        this.leagueRepository = leagueRepository;
        this.matchupService = matchupService;
    }

    @Scheduled(cron = "0 10 0 * * *", zone = "${app.season.zone}")
    public void generateWeeklyMatchups() {
        for (League league : leagueRepository.findAll()) {
            try {
                matchupService.ensureMatchupsThroughCurrentWeek(league.getId());
            } catch (Exception ex) {
                // Don't let one bad league stop scheduling for the rest.
                log.warn("Failed to auto-generate matchups for league {}", league.getId(), ex);
            }
        }
    }

    /**
     * Finalizes all matchups whose calendar week has fully ended. Idempotent —
     * already-FINAL matchups with snapshots are skipped.
     */
    @Scheduled(cron = "0 0 6 * * TUE", zone = "${app.season.zone}")
    public void finalizeCompletedMatchups() {
        for (League league : leagueRepository.findAll()) {
            try {
                int count = matchupService.finalizeCompletedWeeks(league.getId());
                if (count > 0) {
                    log.info("Auto-finalized {} matchup(s) for league {}", count, league.getId());
                }
            } catch (Exception ex) {
                log.warn("Failed to auto-finalize matchups for league {}", league.getId(), ex);
            }
        }
    }
}
