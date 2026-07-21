package com.mlbfantasy.service;

import com.mlbfantasy.exception.ApiException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Maps H2H week numbers (1-based) to Monday-Sunday calendar windows, using a
 * configured season-opening Monday and league time zone.
 */
@Service
public class WeekService {

    private final LocalDate seasonStartMonday;
    private final ZoneId zone;
    private final int totalWeeks;

    public WeekService(
            @Value("${app.season.start-monday}") String startMonday,
            @Value("${app.season.zone}") String zone,
            @Value("${app.season.total-weeks:26}") int totalWeeks) {
        this.seasonStartMonday = LocalDate.parse(startMonday);
        this.zone = ZoneId.of(zone);
        this.totalWeeks = totalWeeks;
    }

    public ZoneId zone() {
        return zone;
    }

    /** Total number of H2H weeks in the season. */
    public int totalWeeks() {
        return totalWeeks;
    }

    /** The current week clamped to the season range [1, totalWeeks]. */
    public int currentSeasonWeek() {
        return Math.min(currentWeek(), totalWeeks);
    }

    public LocalDate weekStart(int weekNumber) {
        if (weekNumber < 1) {
            throw ApiException.badRequest("Week number must be >= 1");
        }
        return seasonStartMonday.plusWeeks(weekNumber - 1L);
    }

    /** Inclusive Sunday end of the given week. */
    public LocalDate weekEnd(int weekNumber) {
        return weekStart(weekNumber).plusDays(6);
    }

    /** The week number that contains "today" in the league zone (min 1). */
    public int currentWeek() {
        LocalDate today = LocalDate.now(zone);
        if (today.isBefore(seasonStartMonday)) {
            return 1;
        }
        long days = ChronoUnit.DAYS.between(seasonStartMonday, today);
        return (int) (days / 7) + 1;
    }
}
