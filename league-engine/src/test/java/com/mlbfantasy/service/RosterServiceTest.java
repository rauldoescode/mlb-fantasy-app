package com.mlbfantasy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mlbfantasy.dto.AddRosterPlayerRequest;
import com.mlbfantasy.exception.ApiException;
import com.mlbfantasy.model.League;
import com.mlbfantasy.model.Player;
import com.mlbfantasy.model.RosterSlot;
import com.mlbfantasy.repository.LeagueMemberRepository;
import com.mlbfantasy.repository.LeagueRepository;
import com.mlbfantasy.repository.PlayerRepository;
import com.mlbfantasy.repository.PlayerScheduledGameRepository;
import com.mlbfantasy.repository.RosterSlotRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

    private RosterService rosterService;

    private final UUID leagueId = UUID.randomUUID();
    private final UUID me = UUID.randomUUID();
    private final UUID otherTeam = UUID.randomUUID();
    private final int playerId = 12345;

    @BeforeEach
    void setUp() {
        LeagueService leagueService = new LeagueService(
                leagueRepository, leagueMemberRepository, null, null, null);
        LineupLockService lineupLockService =
                new LineupLockService(scheduledGameRepository, null);
        rosterService = new RosterService(
                rosterSlotRepository, playerRepository, leagueService, lineupLockService);
    }

    @Test
    void rejectsPlayerAlreadyRosteredByAnotherTeam() {
        stubMembershipAndLeague();
        when(playerRepository.findById(playerId)).thenReturn(Optional.of(emptyPlayer()));
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
        when(playerRepository.findById(playerId)).thenReturn(Optional.of(emptyPlayer()));
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
    void allowsAddingAFreeAgent() {
        stubMembershipAndLeague();
        when(playerRepository.findById(playerId)).thenReturn(Optional.of(emptyPlayer()));
        when(rosterSlotRepository.findFirstByLeagueIdAndPlayerId(leagueId, playerId))
                .thenReturn(Optional.empty());
        when(rosterSlotRepository.countByLeagueIdAndUserId(leagueId, me)).thenReturn(0L);
        when(rosterSlotRepository.save(any(RosterSlot.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = rosterService.addPlayer(
                leagueId, me, new AddRosterPlayerRequest(playerId, "BENCH", true));

        assertThat(response).isNotNull();
        assertThat(response.playerId()).isEqualTo(playerId);
        verify(rosterSlotRepository).save(any(RosterSlot.class));
    }

    private void stubMembershipAndLeague() {
        when(leagueMemberRepository.existsByIdLeagueIdAndIdUserId(leagueId, me)).thenReturn(true);
        when(leagueRepository.findById(leagueId))
                .thenReturn(Optional.of(new League("Test League", 2026, me)));
    }

    private static Player emptyPlayer() {
        try {
            var constructor = Player.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
