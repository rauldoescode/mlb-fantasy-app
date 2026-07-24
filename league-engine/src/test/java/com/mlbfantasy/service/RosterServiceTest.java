package com.mlbfantasy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mlbfantasy.dto.AddRosterPlayerRequest;
import com.mlbfantasy.dto.UpdateRosterSlotRequest;
import com.mlbfantasy.exception.ApiException;
import com.mlbfantasy.model.League;
import com.mlbfantasy.model.LineupEligibility;
import com.mlbfantasy.model.Matchup;
import com.mlbfantasy.model.Player;
import com.mlbfantasy.model.RosterSlot;
import com.mlbfantasy.repository.LeagueMemberRepository;
import com.mlbfantasy.repository.LeagueRepository;
import com.mlbfantasy.repository.LineupEligibilityRepository;
import com.mlbfantasy.repository.MatchupRepository;
import com.mlbfantasy.repository.PlayerRepository;
import com.mlbfantasy.repository.PlayerScheduledGameRepository;
import com.mlbfantasy.repository.RosterSlotRepository;
import java.lang.reflect.Field;
import java.time.LocalDate;
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

/**
 * Uses real collaborator services wired to mocked repository interfaces (rather than
 * mocking the concrete services directly), which keeps the suite portable across JDKs.
 */
@ExtendWith(MockitoExtension.class)
class RosterServiceTest {

    @Mock private RosterSlotRepository rosterSlotRepository;
    @Mock private PlayerRepository playerRepository;
    @Mock private LeagueRepository leagueRepository;
    @Mock private LeagueMemberRepository leagueMemberRepository;
    @Mock private PlayerScheduledGameRepository scheduledGameRepository;
    @Mock private LineupEligibilityRepository lineupEligibilityRepository;
    @Mock private MatchupRepository matchupRepository;

    private RosterService rosterService;
    private WeekService weekService;

    private final UUID leagueId = UUID.randomUUID();
    private final UUID me = UUID.randomUUID();
    private final UUID otherTeam = UUID.randomUUID();
    private final int playerId = 12345;

    @BeforeEach
    void setUp() {
        weekService = new WeekService("2026-03-23", "America/New_York", 26);
        LeagueService leagueService = new LeagueService(
                leagueRepository, leagueMemberRepository, null, null, null, null, null);
        LineupLockService lineupLockService = new LineupLockService(
                scheduledGameRepository,
                lineupEligibilityRepository,
                matchupRepository,
                weekService);
        rosterService = new RosterService(
                rosterSlotRepository, playerRepository, leagueService, lineupLockService);
    }

    @Test
    void rejectsPlayerAlreadyRosteredByAnotherTeam() {
        stubMembershipAndLeague();
        stubLineupEditable();
        when(playerRepository.findById(playerId)).thenReturn(Optional.of(playerWithId(playerId)));
        RosterSlot ownedByOther = new RosterSlot(leagueId, otherTeam, playerId, "BENCH", true);
        when(rosterSlotRepository.findFirstByLeagueIdAndPlayerId(leagueId, playerId))
                .thenReturn(Optional.of(ownedByOther));

        assertThatThrownBy(() -> rosterService.addPlayer(
                leagueId, me, new AddRosterPlayerRequest(playerId, "BENCH", true)))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getMessage()).contains("another team");
                });

        verify(rosterSlotRepository, never()).save(any());
    }

    @Test
    void rejectsPlayerAlreadyOnOwnRoster() {
        stubMembershipAndLeague();
        stubLineupEditable();
        when(playerRepository.findById(playerId)).thenReturn(Optional.of(playerWithId(playerId)));
        RosterSlot ownedByMe = new RosterSlot(leagueId, me, playerId, "BENCH", true);
        when(rosterSlotRepository.findFirstByLeagueIdAndPlayerId(leagueId, playerId))
                .thenReturn(Optional.of(ownedByMe));

        assertThatThrownBy(() -> rosterService.addPlayer(
                leagueId, me, new AddRosterPlayerRequest(playerId, "BENCH", true)))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getMessage()).contains("your roster");
                });

        verify(rosterSlotRepository, never()).save(any());
    }

    @Test
    void allowsAddingAFreeAgentAndWritesEligibility() {
        stubMembershipAndLeague();
        stubLineupEditable();
        when(playerRepository.findById(playerId)).thenReturn(Optional.of(playerWithId(playerId)));
        when(rosterSlotRepository.findFirstByLeagueIdAndPlayerId(leagueId, playerId))
                .thenReturn(Optional.empty());
        when(rosterSlotRepository.countByLeagueIdAndUserId(leagueId, me)).thenReturn(0L);
        when(rosterSlotRepository.save(any(RosterSlot.class))).thenAnswer(inv -> inv.getArgument(0));
        when(scheduledGameRepository.findByPlayerIdAndGameDate(eq(playerId), any(LocalDate.class)))
                .thenReturn(List.of());
        when(lineupEligibilityRepository
                .findByLeagueIdAndUserIdAndWeekNumberAndPlayerIdAndGameDate(
                        eq(leagueId), eq(me), anyInt(), eq(playerId), any(LocalDate.class)))
                .thenReturn(Optional.empty());
        when(lineupEligibilityRepository.save(any(LineupEligibility.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        var response = rosterService.addPlayer(
                leagueId, me, new AddRosterPlayerRequest(playerId, "BENCH", true));

        assertThat(response).isNotNull();
        assertThat(response.playerId()).isEqualTo(playerId);
        verify(rosterSlotRepository).save(any(RosterSlot.class));
        verify(lineupEligibilityRepository, atLeastOnce()).save(any(LineupEligibility.class));
    }

    @Test
    void rejectsStartBenchWhenCurrentWeekMatchupIsFinal() {
        when(leagueMemberRepository.existsByIdLeagueIdAndIdUserId(leagueId, me)).thenReturn(true);
        Matchup finalized = new Matchup(leagueId, weekService.currentSeasonWeek(), me, otherTeam);
        finalized.setStatus("FINAL");
        when(matchupRepository.findForUserInWeek(
                eq(leagueId), eq(weekService.currentSeasonWeek()), eq(me)))
                .thenReturn(Optional.of(finalized));

        assertThatThrownBy(() -> rosterService.addPlayer(
                leagueId, me, new AddRosterPlayerRequest(playerId, "BENCH", true)))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getMessage()).contains("final");
                });

        verify(rosterSlotRepository, never()).save(any());
        verify(lineupEligibilityRepository, never()).save(any());
    }

    @Test
    void updateSlotWritesEligibilityWhenActiveChanges() {
        stubLineupEditable();
        UUID slotId = UUID.randomUUID();
        RosterSlot slot = new RosterSlot(leagueId, me, playerId, "BENCH", false);
        setField(slot, "id", slotId);

        when(rosterSlotRepository.findById(slotId)).thenReturn(Optional.of(slot));
        when(rosterSlotRepository.save(any(RosterSlot.class))).thenAnswer(inv -> inv.getArgument(0));
        when(playerRepository.findById(playerId)).thenReturn(Optional.of(playerWithId(playerId)));
        when(scheduledGameRepository.findByPlayerIdAndGameDate(eq(playerId), any(LocalDate.class)))
                .thenReturn(List.of());
        when(lineupEligibilityRepository
                .findByLeagueIdAndUserIdAndWeekNumberAndPlayerIdAndGameDate(
                        eq(leagueId), eq(me), anyInt(), eq(playerId), any(LocalDate.class)))
                .thenReturn(Optional.empty());
        when(lineupEligibilityRepository.save(any(LineupEligibility.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        rosterService.updateSlot(slotId, me, new UpdateRosterSlotRequest(true, null));

        ArgumentCaptor<LineupEligibility> captor = ArgumentCaptor.forClass(LineupEligibility.class);
        verify(lineupEligibilityRepository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues())
                .isNotEmpty()
                .allMatch(LineupEligibility::isWasStarted)
                .allMatch(row -> playerId == row.getPlayerId());
    }

    private void stubMembershipAndLeague() {
        when(leagueMemberRepository.existsByIdLeagueIdAndIdUserId(leagueId, me)).thenReturn(true);
        when(leagueRepository.findById(leagueId))
                .thenReturn(Optional.of(new League("Test League", 2026, me)));
    }

    private void stubLineupEditable() {
        when(matchupRepository.findForUserInWeek(
                eq(leagueId), eq(weekService.currentSeasonWeek()), eq(me)))
                .thenReturn(Optional.empty());
    }

    private static Player playerWithId(int mlbId) {
        Player player = newInstance(Player.class);
        setField(player, "mlbId", mlbId);
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
}
