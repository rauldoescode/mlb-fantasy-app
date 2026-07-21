package com.mlbfantasy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mlbfantasy.dto.LockPerformanceRequest;
import com.mlbfantasy.dto.PerformanceLockResponse;
import com.mlbfantasy.dto.PlayerGamePerformance;
import com.mlbfantasy.dto.PlayerWeekScore;
import com.mlbfantasy.dto.ScoreBreakdown;
import com.mlbfantasy.dto.WeekScoreResult;
import com.mlbfantasy.exception.ApiException;
import com.mlbfantasy.model.Matchup;
import com.mlbfantasy.model.PerformanceLock;
import com.mlbfantasy.model.RosterSlot;
import com.mlbfantasy.repository.LeagueMemberRepository;
import com.mlbfantasy.repository.LeagueRepository;
import com.mlbfantasy.repository.MatchupRepository;
import com.mlbfantasy.repository.PerformanceLockRepository;
import com.mlbfantasy.repository.RosterSlotRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class PerformanceLockServiceTest {

    @Mock private PerformanceLockRepository performanceLockRepository;
    @Mock private RosterSlotRepository rosterSlotRepository;
    @Mock private MatchupRepository matchupRepository;
    @Mock private LeagueRepository leagueRepository;
    @Mock private LeagueMemberRepository leagueMemberRepository;

    private WeekService weekService;
    private PerformanceLockService service;
    private AtomicReference<WeekScoreResult> liveScore;

    private final UUID leagueId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final int playerId = 101;

    @BeforeEach
    void setUp() {
        weekService = new WeekService("2026-03-23", "America/New_York", 26);
        liveScore = new AtomicReference<>();
        LeagueService leagueService = new LeagueService(
                leagueRepository, leagueMemberRepository, null, null, null);
        ScoringService scoring = new ScoringService(null, null, null, null, null, null, null) {
            @Override
            public WeekScoreResult scoreWeekDetailed(UUID leagueId, UUID userId, int weekNumber) {
                return liveScore.get();
            }
        };
        service = new PerformanceLockService(
                performanceLockRepository,
                rosterSlotRepository,
                matchupRepository,
                leagueService,
                scoring,
                weekService);
    }

    @Test
    void locksBestEligibleGameWhenGamePkOmitted() {
        int week = weekService.currentSeasonWeek();
        stubOwnership(week);
        liveScore.set(weekScoreWithGames(
                game(1001, "4.00", true),
                game(1002, "12.00", true),
                game(1003, "20.00", false)));

        when(performanceLockRepository
                .findByLeagueIdAndUserIdAndWeekNumberAndPlayerId(leagueId, userId, week, playerId))
                .thenReturn(Optional.empty());
        when(performanceLockRepository.save(any(PerformanceLock.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        PerformanceLockResponse response = service.lockPerformance(
                leagueId, week, playerId, userId, new LockPerformanceRequest(null));

        assertThat(response.gamePk()).isEqualTo(1002);
        assertThat(response.autoLocked()).isFalse();
        ArgumentCaptor<PerformanceLock> captor = ArgumentCaptor.forClass(PerformanceLock.class);
        verify(performanceLockRepository).save(captor.capture());
        assertThat(captor.getValue().getGamePk()).isEqualTo(1002);
    }

    @Test
    void rejectsIneligibleRequestedGame() {
        int week = weekService.currentSeasonWeek();
        stubOwnership(week);
        liveScore.set(weekScoreWithGames(
                game(1001, "4.00", true),
                game(1003, "20.00", false)));

        assertThatThrownBy(() -> service.lockPerformance(
                leagueId, week, playerId, userId, new LockPerformanceRequest(1003)))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("not started");
                });

        verify(performanceLockRepository, never()).save(any());
    }

    @Test
    void rejectsWhenMatchupIsFinal() {
        int week = weekService.currentSeasonWeek();
        when(leagueMemberRepository.existsByIdLeagueIdAndIdUserId(leagueId, userId))
                .thenReturn(true);
        Matchup finalized = new Matchup(leagueId, week, userId, UUID.randomUUID());
        finalized.setStatus("FINAL");
        when(matchupRepository.findForUserInWeek(leagueId, week, userId))
                .thenReturn(Optional.of(finalized));

        assertThatThrownBy(() -> service.lockPerformance(
                leagueId, week, playerId, userId, new LockPerformanceRequest(null)))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getMessage()).contains("final");
                });
    }

    @Test
    void unlockDeletesExistingLock() {
        int week = weekService.currentSeasonWeek();
        stubOwnership(week);
        PerformanceLock existing =
                new PerformanceLock(leagueId, userId, week, playerId, 1001, false);
        when(performanceLockRepository
                .findByLeagueIdAndUserIdAndWeekNumberAndPlayerId(leagueId, userId, week, playerId))
                .thenReturn(Optional.of(existing));

        service.unlockPerformance(leagueId, week, playerId, userId);

        verify(performanceLockRepository).delete(existing);
    }

    @Test
    void autoLockBestIfAbsentSkipsWhenManualLockExists() {
        int week = weekService.currentSeasonWeek();
        when(performanceLockRepository
                .findByLeagueIdAndUserIdAndWeekNumberAndPlayerId(leagueId, userId, week, playerId))
                .thenReturn(Optional.of(
                        new PerformanceLock(leagueId, userId, week, playerId, 1001, false)));

        service.autoLockBestIfAbsent(leagueId, week, userId, playerId);

        verify(performanceLockRepository, never()).save(any());
    }

    private void stubOwnership(int week) {
        when(leagueMemberRepository.existsByIdLeagueIdAndIdUserId(leagueId, userId))
                .thenReturn(true);
        when(matchupRepository.findForUserInWeek(eq(leagueId), eq(week), eq(userId)))
                .thenReturn(Optional.empty());
        when(rosterSlotRepository.findByLeagueIdAndUserIdAndPlayerId(leagueId, userId, playerId))
                .thenReturn(Optional.of(new RosterSlot(leagueId, userId, playerId, "START", true)));
    }

    private WeekScoreResult weekScoreWithGames(PlayerGamePerformance... games) {
        List<PlayerGamePerformance> gameList = List.of(games);
        PlayerGamePerformance scoring = ScoringService.selectBestEligibleGame(gameList);
        BigDecimal points = scoring != null ? scoring.points() : BigDecimal.ZERO;
        PlayerWeekScore line = new PlayerWeekScore(
                playerId,
                true,
                points,
                scoring != null ? scoring.gamePk() : null,
                false,
                Map.of(),
                gameList);
        return new WeekScoreResult(
                new ScoreBreakdown(userId, points, new LinkedHashMap<>()),
                List.of(line));
    }

    private static PlayerGamePerformance game(int gamePk, String points, boolean eligible) {
        return new PlayerGamePerformance(
                gamePk,
                LocalDate.of(2026, 3, 23),
                new BigDecimal(points),
                Map.of(),
                eligible);
    }
}
