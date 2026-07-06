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

    public WeekService(
            @Value("${app.season.start-monday}") String startMonday,
            @Value("${app.season.zone}") String zone) {
        this.seasonStartMonday = LocalDate.parse(startMonday);
        this.zone = ZoneId.of(zone);
    }

    public ZoneId zone() {
        return zone;
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
