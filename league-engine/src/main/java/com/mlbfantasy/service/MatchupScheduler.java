package com.mlbfantasy.service;

import com.mlbfantasy.model.League;
import com.mlbfantasy.repository.LeagueRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Automatically keeps every league's H2H schedule up to date so commissioners never
 * have to generate matchups by hand. Runs daily; the read path also lazily fills in any
 * missing weeks, so a missed run (e.g. the server was offline) self-heals on next load.
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
}
