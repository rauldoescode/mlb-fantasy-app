package com.mlbfantasy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mlbfantasy.dto.MatchupDetailResponse;
import com.mlbfantasy.dto.MatchupResponse;
import com.mlbfantasy.dto.PlayerWeekScore;
import com.mlbfantasy.dto.RosterSlotResponse;
import com.mlbfantasy.dto.ScoreBreakdown;
import com.mlbfantasy.dto.SetMatchupLineupRequest;
import com.mlbfantasy.dto.WeekScoreResult;
import com.mlbfantasy.exception.ApiException;
import com.mlbfantasy.model.League;
import com.mlbfantasy.model.LeagueMember;
import com.mlbfantasy.model.LeagueMemberId;
import com.mlbfantasy.model.Matchup;
import com.mlbfantasy.model.Player;
import com.mlbfantasy.model.RosterSlot;
import com.mlbfantasy.repository.LeagueMemberRepository;
import com.mlbfantasy.repository.LeagueRepository;
import com.mlbfantasy.repository.MatchupPlayerScoreRepository;
import com.mlbfantasy.repository.MatchupRepository;
import com.mlbfantasy.repository.PlayerRepository;
import com.mlbfantasy.repository.RosterSlotRepository;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class MatchupServiceTest {

    @Mock private MatchupRepository matchupRepository;
    @Mock private LeagueMemberRepository leagueMemberRepository;
    @Mock private LeagueRepository leagueRepository;
    @Mock private RosterSlotRepository rosterSlotRepository;
    @Mock private PlayerRepository playerRepository;
    @Mock private MatchupPlayerScoreRepository matchupPlayerScoreRepository;
    @Captor private ArgumentCaptor<List<Matchup>> matchupsCaptor;

    private final UUID leagueId = UUID.randomUUID();
    private final UUID commissioner = UUID.randomUUID();

    /** Builds a MatchupService backed by real WeekService/LeagueService + mocked repos. */
    private MatchupService serviceWith(WeekService weekService) {
        return serviceWith(weekService, null, null);
    }

    private MatchupService serviceWith(WeekService weekService,
                                       ScoringService scoringService,
                                       LineupLockService lineupLockService) {
        LeagueService leagueService = new LeagueService(
                leagueRepository, leagueMemberRepository, null, null, null);
        return new MatchupService(
                matchupRepository,
                leagueMemberRepository,
                rosterSlotRepository,
                playerRepository,
                null,
                leagueService,
                scoringService,
                lineupLockService,
                null,
                null,
                weekService);
    }

    private WeekService weeks(String startMonday, int totalWeeks) {
        return new WeekService(startMonday, "UTC", totalWeeks);
    }

    @BeforeEach
    void stubSaveAllEcho() {
        // Most tests care about what gets persisted; echo the saved list back.
        lenientSaveAll();
    }

    private void lenientSaveAll() {
        org.mockito.Mockito.lenient()
                .when(matchupRepository.saveAll(any()))
                .thenAnswer(inv -> new ArrayList<>(inv.getArgument(0)));
    }

    private void stubMembers(int count) {
        List<LeagueMember> members = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            members.add(new LeagueMember(leagueId, UUID.randomUUID(), "Team " + i));
        }
        when(leagueMemberRepository.findByIdLeagueId(leagueId)).thenReturn(members);
    }

    private void stubCommissioner() {
        when(leagueRepository.findById(leagueId))
                .thenReturn(Optional.of(new League("L", 2026, commissioner)));
    }

    @Test
    void generatesRoundRobinPairsForFourTeams() {
        MatchupService service = serviceWith(weeks("2026-03-23", 26));
        stubCommissioner();
        stubMembers(4);
        when(matchupRepository.findByLeagueIdAndWeekNumber(leagueId, 1)).thenReturn(List.of());

        List<MatchupResponse> result = service.generateMatchups(leagueId, commissioner, 1);

        assertThat(result).hasSize(2);
        Set<UUID> players = new java.util.HashSet<>();
        result.forEach(m -> {
            assertThat(m.userOneId()).isNotEqualTo(m.userTwoId());
            players.add(m.userOneId());
            players.add(m.userTwoId());
        });
        assertThat(players).hasSize(4); // every team scheduled exactly once
    }

    @Test
    void oddNumberOfTeamsLeavesOneOnBye() {
        MatchupService service = serviceWith(weeks("2026-03-23", 26));
        stubCommissioner();
        stubMembers(3);
        when(matchupRepository.findByLeagueIdAndWeekNumber(leagueId, 1)).thenReturn(List.of());

        List<MatchupResponse> result = service.generateMatchups(leagueId, commissioner, 1);

        assertThat(result).hasSize(1); // 3 teams -> 1 game, 1 bye
    }

    @Test
    void consecutiveWeeksRotateOpponents() {
        MatchupService service = serviceWith(weeks("2026-03-23", 26));
        stubCommissioner();
        stubMembers(4);
        when(matchupRepository.findByLeagueIdAndWeekNumber(eq(leagueId), any(Integer.class)))
                .thenReturn(List.of());

        List<MatchupResponse> week1 = service.generateMatchups(leagueId, commissioner, 1);
        List<MatchupResponse> week2 = service.generateMatchups(leagueId, commissioner, 2);

        assertThat(pairKeys(week1)).isNotEqualTo(pairKeys(week2));
    }

    @Test
    void rejectsWeekAboveSeasonLength() {
        MatchupService service = serviceWith(weeks("2026-03-23", 26));
        stubCommissioner();

        assertThatThrownBy(() -> service.generateMatchups(leagueId, commissioner, 27))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void rejectsWeekBelowOne() {
        MatchupService service = serviceWith(weeks("2026-03-23", 26));
        stubCommissioner();

        assertThatThrownBy(() -> service.generateMatchups(leagueId, commissioner, 0))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void ensureGeneratesEveryWeekThroughCurrentSeasonWeek() {
        // Start far in the past so currentWeek is huge; capped to totalWeeks (3).
        MatchupService service = serviceWith(weeks("2000-01-03", 3));
        stubMembers(2);
        when(matchupRepository.findByLeagueId(leagueId)).thenReturn(List.of());

        service.ensureMatchupsThroughCurrentWeek(leagueId);

        verify(matchupRepository).saveAll(matchupsCaptor.capture());
        List<Matchup> created = matchupsCaptor.getValue();
        assertThat(created).hasSize(3); // 2 teams -> 1 game/week * 3 weeks
        assertThat(created.stream().map(Matchup::getWeekNumber).collect(Collectors.toSet()))
                .containsExactlyInAnyOrder(1, 2, 3);
    }

    @Test
    void ensureIsIdempotentAndSkipsExistingWeeks() {
        MatchupService service = serviceWith(weeks("2000-01-03", 3));
        stubMembers(2);
        List<Matchup> existing = List.of(
                new Matchup(leagueId, 1, UUID.randomUUID(), UUID.randomUUID()),
                new Matchup(leagueId, 2, UUID.randomUUID(), UUID.randomUUID()),
                new Matchup(leagueId, 3, UUID.randomUUID(), UUID.randomUUID()));
        when(matchupRepository.findByLeagueId(leagueId)).thenReturn(existing);

        service.ensureMatchupsThroughCurrentWeek(leagueId);

        verify(matchupRepository, never()).saveAll(any());
    }

    @Test
    void ensureDoesNothingWithFewerThanTwoTeams() {
        MatchupService service = serviceWith(weeks("2000-01-03", 3));
        stubMembers(1);

        service.ensureMatchupsThroughCurrentWeek(leagueId);

        verify(matchupRepository, never()).saveAll(any());
    }

    @Test
    void getMatchupDetailAttachesBothLineupsForOpenWeek() {
        WeekService weekService = weeks("2026-03-23", 26);
        UUID userOne = UUID.randomUUID();
        UUID userTwo = UUID.randomUUID();
        UUID matchupId = UUID.randomUUID();
        int week = weekService.currentSeasonWeek();
        Matchup matchup = new Matchup(leagueId, week, userOne, userTwo);
        setField(matchup, "id", matchupId);

        WeekScoreResult oneScore = weekScore(userOne, 101, true, "8.00");
        WeekScoreResult twoScore = weekScore(userTwo, 202, false, "0.00");
        AtomicInteger liveScoreCalls = new AtomicInteger();

        ScoringService scoring = new ScoringService(null, null, null, null, null, null, null) {
            @Override
            public WeekScoreResult scoreWeekDetailed(UUID leagueId, UUID userId, int weekNumber) {
                liveScoreCalls.incrementAndGet();
                return userId.equals(userOne) ? oneScore : twoScore;
            }
        };
        LineupLockService locks = unlockedLineupService(weekService);
        MatchupService service = serviceWith(weekService, scoring, locks);

        when(matchupRepository.findById(matchupId)).thenReturn(Optional.of(matchup));
        when(leagueMemberRepository.existsByIdLeagueIdAndIdUserId(leagueId, userOne))
                .thenReturn(true);
        when(rosterSlotRepository.findByLeagueIdAndUserId(leagueId, userOne))
                .thenReturn(List.of(new RosterSlot(leagueId, userOne, 101, "START", true)));
        when(rosterSlotRepository.findByLeagueIdAndUserId(leagueId, userTwo))
                .thenReturn(List.of(new RosterSlot(leagueId, userTwo, 202, "BENCH", false)));
        when(leagueMemberRepository.findById(new LeagueMemberId(leagueId, userOne)))
                .thenReturn(Optional.of(new LeagueMember(leagueId, userOne, "Aces")));
        when(leagueMemberRepository.findById(new LeagueMemberId(leagueId, userTwo)))
                .thenReturn(Optional.of(new LeagueMember(leagueId, userTwo, "Bombers")));
        when(playerRepository.findAllById(any())).thenAnswer(inv -> {
            Iterable<Integer> ids = inv.getArgument(0);
            List<Player> players = new ArrayList<>();
            for (Integer id : ids) {
                players.add(playerWith(id, "Player " + id, "P"));
            }
            return players;
        });
        when(matchupRepository.save(any(Matchup.class))).thenAnswer(inv -> inv.getArgument(0));

        MatchupDetailResponse detail = service.getMatchupDetail(matchupId, userOne);

        assertThat(liveScoreCalls.get()).isEqualTo(2);
        assertThat(detail.userOneLineup().teamName()).isEqualTo("Aces");
        assertThat(detail.userOneLineup().starters()).hasSize(1);
        assertThat(detail.userOneLineup().starters().get(0).playerId()).isEqualTo(101);
        assertThat(detail.userOneLineup().starters().get(0).weekPoints())
                .isEqualByComparingTo(new BigDecimal("8.00"));
        assertThat(detail.userTwoLineup().teamName()).isEqualTo("Bombers");
        assertThat(detail.userTwoLineup().bench()).hasSize(1);
        assertThat(detail.userTwoLineup().bench().get(0).playerId()).isEqualTo(202);
        assertThat(detail.userOneBreakdown().totalPoints())
                .isEqualByComparingTo(new BigDecimal("8.00"));
        assertThat(detail.lineupEditable()).isTrue();
    }

    @Test
    void getMatchupDetailUsesSnapshotsWhenFinal() {
        WeekService weekService = weeks("2026-03-23", 26);
        UUID userOne = UUID.randomUUID();
        UUID userTwo = UUID.randomUUID();
        UUID matchupId = UUID.randomUUID();
        int week = weekService.currentSeasonWeek();
        Matchup matchup = new Matchup(leagueId, week, userOne, userTwo);
        setField(matchup, "id", matchupId);
        matchup.setStatus("FINAL");
        matchup.setUserOneScore(new BigDecimal("12.00"));
        matchup.setUserTwoScore(new BigDecimal("5.00"));

        AtomicInteger liveScoreCalls = new AtomicInteger();
        AtomicInteger snapshotCalls = new AtomicInteger();

        ScoringService scoring = new ScoringService(null, null, null, null, null, null, null) {
            @Override
            public WeekScoreResult scoreWeekDetailed(UUID leagueId, UUID userId, int weekNumber) {
                liveScoreCalls.incrementAndGet();
                return weekScore(userId, 1, true, "0.00");
            }

            @Override
            public WeekScoreResult scoreWeekFromSnapshot(UUID matchupIdArg, UUID userId) {
                snapshotCalls.incrementAndGet();
                return userId.equals(userOne)
                        ? weekScore(userOne, 101, true, "11.00")
                        : weekScore(userTwo, 202, true, "4.00");
            }
        };
        MatchupService service = serviceWith(weekService, scoring, unlockedLineupService(weekService));

        when(matchupRepository.findById(matchupId)).thenReturn(Optional.of(matchup));
        when(leagueMemberRepository.existsByIdLeagueIdAndIdUserId(leagueId, commissioner))
                .thenReturn(true);
        when(rosterSlotRepository.findByLeagueIdAndUserId(eq(leagueId), any(UUID.class)))
                .thenReturn(List.of());
        when(leagueMemberRepository.findById(any(LeagueMemberId.class))).thenReturn(Optional.empty());
        when(playerRepository.findAllById(any())).thenReturn(List.of());

        MatchupDetailResponse detail = service.getMatchupDetail(matchupId, commissioner);

        assertThat(snapshotCalls.get()).isEqualTo(2);
        assertThat(liveScoreCalls.get()).isZero();
        assertThat(detail.userOneBreakdown().totalPoints())
                .isEqualByComparingTo(new BigDecimal("12.00"));
        assertThat(detail.userTwoBreakdown().totalPoints())
                .isEqualByComparingTo(new BigDecimal("5.00"));
        assertThat(detail.lineupEditable()).isFalse();
        verify(matchupRepository, never()).save(any());
    }

    @Test
    void setMatchupLineupRejectsPastWeek() {
        WeekService weekService = weeks("2026-03-23", 26);
        UUID userOne = UUID.randomUUID();
        UUID userTwo = UUID.randomUUID();
        UUID matchupId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();
        // Week 1 is before the current season week (today is mid-season in real time).
        Matchup matchup = new Matchup(leagueId, 1, userOne, userTwo);
        setField(matchup, "id", matchupId);

        MatchupService service = serviceWith(
                weekService, null, unlockedLineupService(weekService));

        when(matchupRepository.findById(matchupId)).thenReturn(Optional.of(matchup));
        when(leagueMemberRepository.existsByIdLeagueIdAndIdUserId(leagueId, userOne))
                .thenReturn(true);

        assertThatThrownBy(() -> service.setMatchupLineup(
                matchupId, slotId, userOne, new SetMatchupLineupRequest(true)))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getMessage()).contains("current week");
                });
    }

    @Test
    void setMatchupLineupUpdatesSlotForCurrentOpenWeek() {
        WeekService weekService = weeks("2026-03-23", 26);
        UUID userOne = UUID.randomUUID();
        UUID userTwo = UUID.randomUUID();
        UUID matchupId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();
        int week = weekService.currentSeasonWeek();
        Matchup matchup = new Matchup(leagueId, week, userOne, userTwo);
        setField(matchup, "id", matchupId);

        RosterSlot slot = new RosterSlot(leagueId, userOne, 101, "BENCH", false);
        setField(slot, "id", slotId);

        AtomicInteger updates = new AtomicInteger();
        RosterService rosterStub = new RosterService(null, null, null, unlockedLineupService(weekService)) {
            @Override
            public RosterSlotResponse updateSlot(UUID id, UUID userId,
                                                 com.mlbfantasy.dto.UpdateRosterSlotRequest request) {
                updates.incrementAndGet();
                assertThat(request.active()).isTrue();
                return new RosterSlotResponse(
                        id, 101, "Player 101", "P", "NYY", null, "BENCH", true, false);
            }
        };

        LeagueService leagueService = new LeagueService(
                leagueRepository, leagueMemberRepository, null, null, null);
        MatchupService service = new MatchupService(
                matchupRepository,
                leagueMemberRepository,
                rosterSlotRepository,
                playerRepository,
                null,
                leagueService,
                null,
                unlockedLineupService(weekService),
                rosterStub,
                null,
                weekService);

        when(matchupRepository.findById(matchupId)).thenReturn(Optional.of(matchup));
        when(leagueMemberRepository.existsByIdLeagueIdAndIdUserId(leagueId, userOne))
                .thenReturn(true);
        when(rosterSlotRepository.findById(slotId)).thenReturn(Optional.of(slot));

        RosterSlotResponse response = service.setMatchupLineup(
                matchupId, slotId, userOne, new SetMatchupLineupRequest(true));

        assertThat(updates.get()).isEqualTo(1);
        assertThat(response.active()).isTrue();
        assertThat(response.playerId()).isEqualTo(101);
    }

    @Test
    void finalizeMatchupInternalWritesSnapshotsAndIsIdempotent() {
        WeekService weekService = weeks("2026-03-23", 26);
        UUID userOne = UUID.randomUUID();
        UUID userTwo = UUID.randomUUID();
        UUID matchupId = UUID.randomUUID();
        Matchup matchup = new Matchup(leagueId, 1, userOne, userTwo);
        setField(matchup, "id", matchupId);

        AtomicInteger scoreCalls = new AtomicInteger();
        AtomicInteger autoLockCalls = new AtomicInteger();
        AtomicInteger snapshotSaves = new AtomicInteger();

        ScoringService scoring = new ScoringService(null, null, null, null, null, null, null) {
            @Override
            public WeekScoreResult scoreWeekDetailed(UUID leagueId, UUID userId, int weekNumber) {
                scoreCalls.incrementAndGet();
                return userId.equals(userOne)
                        ? weekScore(userOne, 101, true, "10.00")
                        : weekScore(userTwo, 202, true, "7.00");
            }
        };
        PerformanceLockService locks = new PerformanceLockService(
                null, null, null, null, scoring, weekService) {
            @Override
            public void autoLockBestIfAbsent(UUID leagueId, int weekNumber, UUID userId, Integer playerId) {
                autoLockCalls.incrementAndGet();
            }
        };
        AtomicBoolean snapshotExists = new AtomicBoolean(false);
        when(matchupPlayerScoreRepository.existsByMatchupId(matchupId))
                .thenAnswer(inv -> snapshotExists.get());
        when(matchupPlayerScoreRepository.saveAll(any())).thenAnswer(inv -> {
            List<?> entities = inv.getArgument(0);
            snapshotSaves.addAndGet(entities.size());
            snapshotExists.set(!entities.isEmpty());
            return new ArrayList<>();
        });

        LeagueService leagueService = new LeagueService(
                leagueRepository, leagueMemberRepository, null, null, null);
        MatchupService service = new MatchupService(
                matchupRepository,
                leagueMemberRepository,
                rosterSlotRepository,
                playerRepository,
                matchupPlayerScoreRepository,
                leagueService,
                scoring,
                unlockedLineupService(weekService),
                null,
                locks,
                weekService);

        when(rosterSlotRepository.findByLeagueIdAndUserId(eq(leagueId), any(UUID.class)))
                .thenReturn(List.of());
        when(matchupRepository.save(any(Matchup.class))).thenAnswer(inv -> inv.getArgument(0));

        MatchupResponse first = service.finalizeMatchupInternal(matchup);
        assertThat(first.status()).isEqualTo("FINAL");
        assertThat(first.winnerId()).isEqualTo(userOne);
        assertThat(first.userOneScore()).isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(autoLockCalls.get()).isEqualTo(2);
        assertThat(snapshotSaves.get()).isEqualTo(2);
        assertThat(matchup.getFinalizedAt()).isNotNull();

        int scoresAfterFirst = scoreCalls.get();
        MatchupResponse second = service.finalizeMatchupInternal(matchup);
        assertThat(second.status()).isEqualTo("FINAL");
        assertThat(scoreCalls.get()).isEqualTo(scoresAfterFirst); // idempotent skip
    }

    @Test
    void finalizeCompletedWeeksSkipsCurrentInProgressWeek() {
        WeekService weekService = weeks(LocalDate.now().with(java.time.DayOfWeek.MONDAY).toString(), 26);

        LeagueService leagueService = new LeagueService(
                leagueRepository, leagueMemberRepository, null, null, null);
        MatchupService service = new MatchupService(
                matchupRepository,
                leagueMemberRepository,
                rosterSlotRepository,
                playerRepository,
                null,
                leagueService,
                null,
                unlockedLineupService(weekService),
                null,
                null,
                weekService);

        int count = service.finalizeCompletedWeeks(leagueId);
        assertThat(count).isZero();
        verify(matchupRepository, never()).save(any());
        verify(matchupRepository, never()).findByLeagueIdAndWeekNumber(any(), any(Integer.class));
    }

    private static LineupLockService unlockedLineupService(WeekService weekService) {
        return new LineupLockService(null, null, null, weekService) {
            @Override
            public boolean isPlayerLocked(Integer playerId) {
                return false;
            }

            @Override
            public void materializeTodaysLockIfNeeded(UUID leagueId,
                                                     UUID userId,
                                                     Integer playerId,
                                                     boolean currentlyActive) {
                // no-op
            }

            @Override
            public boolean isMatchupLineupEditable(Matchup matchup, UUID requesterId) {
                return matchup.getWeekNumber() == weekService.currentSeasonWeek()
                        && !"FINAL".equals(matchup.getStatus())
                        && (requesterId.equals(matchup.getUserOneId())
                                || requesterId.equals(matchup.getUserTwoId()));
            }

            @Override
            public void requireMatchupLineupEditable(Matchup matchup, UUID userId) {
                if (!isMatchupLineupEditable(matchup, userId)) {
                    throw ApiException.conflict("Lineup changes are only allowed for the current week");
                }
            }
        };
    }

    private static WeekScoreResult weekScore(UUID userId, int playerId, boolean active, String points) {
        BigDecimal pts = new BigDecimal(points);
        PlayerWeekScore line = new PlayerWeekScore(
                playerId, active, pts, 1, false, Map.of(), List.of());
        return new WeekScoreResult(
                new ScoreBreakdown(userId, pts, new LinkedHashMap<>()),
                List.of(line));
    }

    private static Player playerWith(int mlbId, String name, String pos) {
        Player player = newInstance(Player.class);
        setField(player, "mlbId", mlbId);
        setField(player, "fullName", name);
        setField(player, "primaryPos", pos);
        return player;
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

    private static Set<String> pairKeys(List<MatchupResponse> matchups) {
        return matchups.stream()
                .map(m -> {
                    List<String> ids = new ArrayList<>(
                            List.of(m.userOneId().toString(), m.userTwoId().toString()));
                    java.util.Collections.sort(ids);
                    return String.join("-", ids);
                })
                .collect(Collectors.toSet());
    }
}
