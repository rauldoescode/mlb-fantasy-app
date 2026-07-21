package com.mlbfantasy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mlbfantasy.dto.MatchupResponse;
import com.mlbfantasy.exception.ApiException;
import com.mlbfantasy.model.League;
import com.mlbfantasy.model.LeagueMember;
import com.mlbfantasy.model.Matchup;
import com.mlbfantasy.repository.LeagueMemberRepository;
import com.mlbfantasy.repository.LeagueRepository;
import com.mlbfantasy.repository.MatchupRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
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
    @Captor private ArgumentCaptor<List<Matchup>> matchupsCaptor;

    private final UUID leagueId = UUID.randomUUID();
    private final UUID commissioner = UUID.randomUUID();

    /** Builds a MatchupService backed by real WeekService/LeagueService + mocked repos. */
    private MatchupService serviceWith(WeekService weekService) {
        LeagueService leagueService = new LeagueService(
                leagueRepository, leagueMemberRepository, null, null, null);
        return new MatchupService(
                matchupRepository, leagueMemberRepository, leagueService, null, weekService);
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
