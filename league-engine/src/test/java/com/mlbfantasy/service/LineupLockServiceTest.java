package com.mlbfantasy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mlbfantasy.exception.ApiException;
import com.mlbfantasy.model.LineupEligibility;
import com.mlbfantasy.model.Matchup;
import com.mlbfantasy.model.PlayerScheduledGame;
import com.mlbfantasy.repository.LineupEligibilityRepository;
import com.mlbfantasy.repository.MatchupRepository;
import com.mlbfantasy.repository.PlayerScheduledGameRepository;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class LineupLockServiceTest {

    @Mock private PlayerScheduledGameRepository scheduledGameRepository;
    @Mock private LineupEligibilityRepository lineupEligibilityRepository;
    @Mock private MatchupRepository matchupRepository;

    private WeekService weekService;
    private LineupLockService service;

    private final UUID leagueId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final int playerId = 42;

    @BeforeEach
    void setUp() {
        weekService = new WeekService("2026-03-23", "America/New_York", 26);
        service = new LineupLockService(
                scheduledGameRepository,
                lineupEligibilityRepository,
                matchupRepository,
                weekService);
    }

    @Test
    void isPlayerLockedWhenTodaysGameHasStarted() {
        LocalDate today = LocalDate.now(weekService.zone());
        PlayerScheduledGame game = scheduledGame(playerId, today, OffsetDateTime.now().minusMinutes(5));
        when(scheduledGameRepository.findByPlayerIdAndGameDate(playerId, today)).thenReturn(List.of(game));

        assertThat(service.isPlayerLocked(playerId)).isTrue();
    }

    @Test
    void isPlayerUnlockedWhenNoGameToday() {
        LocalDate today = LocalDate.now(weekService.zone());
        when(scheduledGameRepository.findByPlayerIdAndGameDate(playerId, today)).thenReturn(List.of());

        assertThat(service.isPlayerLocked(playerId)).isFalse();
    }

    @Test
    void requireLineupEditableRejectsFinalCurrentWeekMatchup() {
        int week = weekService.currentSeasonWeek();
        Matchup finalized = new Matchup(leagueId, week, userId, UUID.randomUUID());
        finalized.setStatus("FINAL");
        when(matchupRepository.findForUserInWeek(leagueId, week, userId))
                .thenReturn(Optional.of(finalized));

        assertThatThrownBy(() -> service.requireLineupEditable(leagueId, userId))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getMessage()).contains("final");
                });
    }

    @Test
    void requireMatchupLineupEditableRejectsPastWeek() {
        Matchup past = new Matchup(leagueId, 1, userId, UUID.randomUUID());
        assertThat(weekService.currentSeasonWeek()).isGreaterThan(1);

        assertThatThrownBy(() -> service.requireMatchupLineupEditable(past, userId))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getMessage()).contains("current week");
                });
    }

    @Test
    void syncOpenWeekEligibilityDoesNotOverwriteFrozenRow() {
        int week = weekService.currentSeasonWeek();
        LocalDate today = LocalDate.now(weekService.zone());
        when(scheduledGameRepository.findByPlayerIdAndGameDate(eq(playerId), any(LocalDate.class)))
                .thenReturn(List.of());

        LineupEligibility frozen = new LineupEligibility(
                leagueId, userId, week, playerId, today, true);
        frozen.setLockedAt(OffsetDateTime.now().minusHours(1));
        when(lineupEligibilityRepository
                .findByLeagueIdAndUserIdAndWeekNumberAndPlayerIdAndGameDate(
                        eq(leagueId), eq(userId), eq(week), eq(playerId), any(LocalDate.class)))
                .thenReturn(Optional.of(frozen));

        service.syncOpenWeekEligibility(leagueId, userId, playerId, false);

        verify(lineupEligibilityRepository, never()).save(any());
    }

    @Test
    void materializeTodaysLockCreatesFrozenRowWhenMissing() {
        int week = weekService.currentSeasonWeek();
        LocalDate today = LocalDate.now(weekService.zone());
        PlayerScheduledGame game = scheduledGame(playerId, today, OffsetDateTime.now().minusMinutes(1));
        when(scheduledGameRepository.findByPlayerIdAndGameDate(playerId, today)).thenReturn(List.of(game));
        when(lineupEligibilityRepository
                .findByLeagueIdAndUserIdAndWeekNumberAndPlayerIdAndGameDate(
                        leagueId, userId, week, playerId, today))
                .thenReturn(Optional.empty());
        when(lineupEligibilityRepository.save(any(LineupEligibility.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.materializeTodaysLockIfNeeded(leagueId, userId, playerId, true);

        ArgumentCaptor<LineupEligibility> captor = ArgumentCaptor.forClass(LineupEligibility.class);
        verify(lineupEligibilityRepository).save(captor.capture());
        assertThat(captor.getValue().isWasStarted()).isTrue();
        assertThat(captor.getValue().getLockedAt()).isNotNull();
    }

    private static PlayerScheduledGame scheduledGame(int playerId,
                                                     LocalDate date,
                                                     OffsetDateTime start) {
        PlayerScheduledGame game = newInstance(PlayerScheduledGame.class);
        setField(game, "playerId", playerId);
        setField(game, "gameDate", date);
        setField(game, "gameStartTime", start);
        setField(game, "gamePk", 999);
        return game;
    }

    private static <T> T newInstance(Class<T> type) {
        try {
            var constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
