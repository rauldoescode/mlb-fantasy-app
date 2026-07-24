package com.mlbfantasy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mlbfantasy.dto.CreateLeagueRequest;
import com.mlbfantasy.dto.LeagueResponse;
import com.mlbfantasy.dto.ScoringRulesResponse;
import com.mlbfantasy.dto.UpdateLeagueSettingsRequest;
import com.mlbfantasy.exception.ApiException;
import com.mlbfantasy.model.League;
import com.mlbfantasy.model.LeagueMember;
import com.mlbfantasy.model.LeagueVisibility;
import com.mlbfantasy.model.Player;
import com.mlbfantasy.model.RosterSlot;
import com.mlbfantasy.model.ScoringRule;
import com.mlbfantasy.repository.LeagueMemberRepository;
import com.mlbfantasy.repository.LeagueRepository;
import com.mlbfantasy.repository.MatchupRepository;
import com.mlbfantasy.repository.PlayerRepository;
import com.mlbfantasy.repository.RosterSlotRepository;
import com.mlbfantasy.repository.ScoringRuleRepository;
import com.mlbfantasy.repository.UserRepository;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class LeagueServiceTest {

    @Mock private LeagueRepository leagueRepository;
    @Mock private LeagueMemberRepository leagueMemberRepository;
    @Mock private ScoringRuleRepository scoringRuleRepository;
    @Mock private MatchupRepository matchupRepository;
    @Mock private UserRepository userRepository;
    @Mock private RosterSlotRepository rosterSlotRepository;
    @Mock private PlayerRepository playerRepository;

    private LeagueService leagueService;

    private final UUID commissionerId = UUID.randomUUID();
    private final UUID memberId = UUID.randomUUID();
    private final UUID leagueId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        leagueService = new LeagueService(leagueRepository, leagueMemberRepository,
                scoringRuleRepository, matchupRepository, userRepository,
                rosterSlotRepository, playerRepository);
    }

    @Test
    void createLeagueRetriesOnJoinCodeCollision() {
        when(leagueRepository.findByJoinCode(any()))
                .thenReturn(Optional.of(new League("Other", 2026, UUID.randomUUID())))
                .thenReturn(Optional.empty());
        when(leagueRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreateLeagueRequest request = new CreateLeagueRequest(
                "My League", 2026, "My Team", LeagueVisibility.PUBLIC, 10, null, null);

        leagueService.createLeague(commissionerId, request);

        verify(leagueRepository, times(2)).findByJoinCode(any());
        verify(leagueRepository).save(any(League.class));
        verify(leagueMemberRepository).save(any(LeagueMember.class));
    }

    @Test
    void createLeagueRejectsOddMaxMembers() {
        assertApiStatus(HttpStatus.BAD_REQUEST, () -> leagueService.createLeague(commissionerId,
                new CreateLeagueRequest("L", 2026, "T", null, 7, null, null)));
    }

    @Test
    void createLeagueRejectsOutOfRangeMaxMembers() {
        assertApiStatus(HttpStatus.BAD_REQUEST, () -> leagueService.createLeague(commissionerId,
                new CreateLeagueRequest("L", 2026, "T", null, 13, null, null)));
        assertApiStatus(HttpStatus.BAD_REQUEST, () -> leagueService.createLeague(commissionerId,
                new CreateLeagueRequest("L", 2026, "T", null, 1, null, null)));
    }

    @Test
    void joinPublicLeagueRejectsPrivateLeague() {
        League league = league(LeagueVisibility.PRIVATE, 10);
        when(leagueRepository.findById(leagueId)).thenReturn(Optional.of(league));

        assertApiStatus(HttpStatus.BAD_REQUEST,
                () -> leagueService.joinPublicLeague(leagueId, memberId, "Team"));
    }

    @Test
    void joinPublicLeagueRejectsExistingMember() {
        League league = league(LeagueVisibility.PUBLIC, 10);
        when(leagueRepository.findById(leagueId)).thenReturn(Optional.of(league));
        when(leagueMemberRepository.existsByIdLeagueIdAndIdUserId(leagueId, memberId))
                .thenReturn(true);

        assertApiStatus(HttpStatus.CONFLICT,
                () -> leagueService.joinPublicLeague(leagueId, memberId, "Team"));
    }

    @Test
    void joinByCodeRejectsExistingMember() {
        League league = league(LeagueVisibility.PRIVATE, 10);
        when(leagueRepository.findByJoinCode("CODE1234")).thenReturn(Optional.of(league));
        when(leagueMemberRepository.existsByIdLeagueIdAndIdUserId(leagueId, memberId))
                .thenReturn(true);

        assertApiStatus(HttpStatus.CONFLICT,
                () -> leagueService.joinLeagueByCode("CODE1234", memberId, "Team"));
    }

    @Test
    void joinByCodeRejectsUnknownCode() {
        when(leagueRepository.findByJoinCode("NOPE")).thenReturn(Optional.empty());

        assertApiStatus(HttpStatus.NOT_FOUND,
                () -> leagueService.joinLeagueByCode("NOPE", memberId, "Team"));
    }

    @Test
    void joinPublicLeagueRejectsWhenAtCapacity() {
        League league = league(LeagueVisibility.PUBLIC, 4);
        when(leagueRepository.findById(leagueId)).thenReturn(Optional.of(league));
        when(leagueMemberRepository.existsByIdLeagueIdAndIdUserId(leagueId, memberId))
                .thenReturn(false);
        when(leagueMemberRepository.countByIdLeagueId(leagueId)).thenReturn(4L);

        assertApiStatus(HttpStatus.CONFLICT,
                () -> leagueService.joinPublicLeague(leagueId, memberId, "Team"));
    }

    @Test
    void joinByCodeRejectsWhenAtCapacity() {
        League league = league(LeagueVisibility.PRIVATE, 4);
        when(leagueRepository.findByJoinCode("CODE1234")).thenReturn(Optional.of(league));
        when(leagueMemberRepository.existsByIdLeagueIdAndIdUserId(leagueId, memberId))
                .thenReturn(false);
        when(leagueMemberRepository.countByIdLeagueId(leagueId)).thenReturn(4L);

        assertApiStatus(HttpStatus.CONFLICT,
                () -> leagueService.joinLeagueByCode("CODE1234", memberId, "Team"));
    }

    @Test
    void addMemberRejectsWhenAtCapacity() {
        League league = league(LeagueVisibility.PRIVATE, 4);
        // requireCommissioner + requireLeague both load the league.
        when(leagueRepository.findById(leagueId)).thenReturn(Optional.of(league));
        var invitee = new com.mlbfantasy.model.User("x@y.com", "Invitee", "hash");
        setField(invitee, "id", memberId);
        when(userRepository.findByEmail("x@y.com")).thenReturn(Optional.of(invitee));
        when(leagueMemberRepository.existsByIdLeagueIdAndIdUserId(leagueId, memberId))
                .thenReturn(false);
        when(leagueMemberRepository.countByIdLeagueId(leagueId)).thenReturn(4L);

        assertApiStatus(HttpStatus.CONFLICT, () -> leagueService.addMember(leagueId, commissionerId,
                new com.mlbfantasy.dto.AddMemberRequest("x@y.com", "Team")));
    }

    @Test
    void regenerateJoinCodeRejectsNonCommissioner() {
        League league = league(LeagueVisibility.PRIVATE, 10);
        when(leagueRepository.findById(leagueId)).thenReturn(Optional.of(league));

        assertApiStatus(HttpStatus.FORBIDDEN,
                () -> leagueService.regenerateJoinCode(leagueId, memberId));
    }

    @Test
    void updateVisibilityRejectsNonCommissioner() {
        League league = league(LeagueVisibility.PRIVATE, 10);
        when(leagueRepository.findById(leagueId)).thenReturn(Optional.of(league));

        assertApiStatus(HttpStatus.FORBIDDEN, () -> leagueService.updateVisibility(
                leagueId, memberId, LeagueVisibility.PUBLIC));
    }

    @Test
    void regenerateJoinCodeSucceedsForCommissioner() {
        League league = league(LeagueVisibility.PRIVATE, 10);
        String original = league.getJoinCode();
        when(leagueRepository.findById(leagueId)).thenReturn(Optional.of(league));
        when(leagueRepository.findByJoinCode(any())).thenReturn(Optional.empty());
        when(leagueRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(leagueMemberRepository.countByIdLeagueId(leagueId)).thenReturn(1L);

        var response = leagueService.regenerateJoinCode(leagueId, commissionerId);

        assertThat(response.joinCode()).isNotBlank().isNotEqualTo(original);
    }

    @Test
    void updateSettingsSucceedsForCommissioner() {
        League league = league(LeagueVisibility.PRIVATE, 10);
        when(leagueRepository.findById(leagueId)).thenReturn(Optional.of(league));
        when(leagueRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(leagueMemberRepository.countByIdLeagueId(leagueId)).thenReturn(2L);
        when(rosterSlotRepository.findByLeagueId(leagueId)).thenReturn(List.of());

        LeagueResponse response = leagueService.updateSettings(
                leagueId,
                commissionerId,
                new UpdateLeagueSettingsRequest(
                        " Renamed League ",
                        new BigDecimal("75000000.00"),
                        12,
                        8));

        assertThat(response.name()).isEqualTo("Renamed League");
        assertThat(response.salaryCap()).isEqualByComparingTo("75000000.00");
        assertThat(response.rosterSize()).isEqualTo(12);
        assertThat(response.maxMembers()).isEqualTo(8);
        assertThat(response.memberCount()).isEqualTo(2);
        verify(leagueRepository).save(league);
    }

    @Test
    void updateSettingsRejectsNonCommissioner() {
        League league = league(LeagueVisibility.PRIVATE, 10);
        when(leagueRepository.findById(leagueId)).thenReturn(Optional.of(league));

        assertApiStatus(HttpStatus.FORBIDDEN, () -> leagueService.updateSettings(
                leagueId,
                memberId,
                new UpdateLeagueSettingsRequest("Nope", null, null, null)));
        verify(leagueRepository, never()).save(any());
    }

    @Test
    void updateSettingsRejectsBlankName() {
        League league = league(LeagueVisibility.PRIVATE, 10);
        when(leagueRepository.findById(leagueId)).thenReturn(Optional.of(league));

        assertApiStatus(HttpStatus.BAD_REQUEST, () -> leagueService.updateSettings(
                leagueId,
                commissionerId,
                new UpdateLeagueSettingsRequest("   ", null, null, null)));
    }

    @Test
    void updateSettingsRejectsMaxMembersBelowMembership() {
        League league = league(LeagueVisibility.PRIVATE, 10);
        when(leagueRepository.findById(leagueId)).thenReturn(Optional.of(league));
        when(leagueMemberRepository.countByIdLeagueId(leagueId)).thenReturn(8L);

        assertApiStatus(HttpStatus.CONFLICT, () -> leagueService.updateSettings(
                leagueId,
                commissionerId,
                new UpdateLeagueSettingsRequest(null, null, null, 6)));
        verify(leagueRepository, never()).save(any());
    }

    @Test
    void updateSettingsRejectsOddMaxMembers() {
        League league = league(LeagueVisibility.PRIVATE, 10);
        when(leagueRepository.findById(leagueId)).thenReturn(Optional.of(league));

        assertApiStatus(HttpStatus.BAD_REQUEST, () -> leagueService.updateSettings(
                leagueId,
                commissionerId,
                new UpdateLeagueSettingsRequest(null, null, null, 7)));
    }

    @Test
    void updateSettingsRejectsRosterSizeBelowLargestRoster() {
        League league = league(LeagueVisibility.PRIVATE, 10);
        when(leagueRepository.findById(leagueId)).thenReturn(Optional.of(league));
        when(rosterSlotRepository.findByLeagueId(leagueId)).thenReturn(List.of(
                slot(memberId, 101),
                slot(memberId, 102),
                slot(memberId, 103),
                slot(memberId, 104),
                slot(memberId, 105),
                slot(memberId, 106),
                slot(commissionerId, 201)));

        assertApiStatus(HttpStatus.CONFLICT, () -> leagueService.updateSettings(
                leagueId,
                commissionerId,
                new UpdateLeagueSettingsRequest(null, null, 5, null)));
        verify(leagueRepository, never()).save(any());
    }

    @Test
    void updateSettingsRejectsSalaryCapBelowTeamSpend() {
        League league = league(LeagueVisibility.PRIVATE, 10);
        when(leagueRepository.findById(leagueId)).thenReturn(Optional.of(league));
        when(rosterSlotRepository.findByLeagueId(leagueId)).thenReturn(List.of(
                slot(memberId, 101),
                slot(memberId, 102)));
        when(playerRepository.findAllById(any())).thenReturn(List.of(
                playerWithSalary(101, "30000000"),
                playerWithSalary(102, "25000000")));

        assertApiStatus(HttpStatus.CONFLICT, () -> leagueService.updateSettings(
                leagueId,
                commissionerId,
                new UpdateLeagueSettingsRequest(null, new BigDecimal("40000000"), null, null)));
        verify(leagueRepository, never()).save(any());
    }

    @Test
    void updateSettingsAllowsRaisingSalaryCapAndRosterSize() {
        League league = league(LeagueVisibility.PRIVATE, 10);
        league.setSalaryCap(new BigDecimal("50000000"));
        league.setRosterSize(10);
        when(leagueRepository.findById(leagueId)).thenReturn(Optional.of(league));
        when(leagueRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(leagueMemberRepository.countByIdLeagueId(leagueId)).thenReturn(2L);
        when(rosterSlotRepository.findByLeagueId(leagueId)).thenReturn(List.of(
                slot(memberId, 101)));
        when(playerRepository.findAllById(any())).thenReturn(List.of(
                playerWithSalary(101, "20000000")));

        LeagueResponse response = leagueService.updateSettings(
                leagueId,
                commissionerId,
                new UpdateLeagueSettingsRequest(
                        null,
                        new BigDecimal("80000000"),
                        14,
                        null));

        assertThat(response.salaryCap()).isEqualByComparingTo("80000000");
        assertThat(response.rosterSize()).isEqualTo(14);
    }

    @Test
    void getScoringRulesReturnsDefaultsForMissingCategories() {
        when(leagueMemberRepository.existsByIdLeagueIdAndIdUserId(leagueId, memberId))
                .thenReturn(true);
        when(scoringRuleRepository.findById(leagueId)).thenReturn(Optional.of(
                new ScoringRule(leagueId, Map.of("home_runs", 6.0))));

        ScoringRulesResponse response = leagueService.getScoringRules(leagueId, memberId);

        assertThat(response.leagueId()).isEqualTo(leagueId);
        assertThat(response.pointValues().get("home_runs")).isEqualTo(6.0);
        assertThat(response.pointValues().get("rbi")).isEqualTo(1.0);
        assertThat(response.pointValues()).containsKeys(
                ScoringService.SCORING_CATEGORIES.toArray(String[]::new));
    }

    @Test
    void updateScoringRulesSucceedsForCommissioner() {
        League league = league(LeagueVisibility.PRIVATE, 10);
        when(leagueRepository.findById(leagueId)).thenReturn(Optional.of(league));
        when(scoringRuleRepository.findById(leagueId)).thenReturn(Optional.empty());
        when(scoringRuleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ScoringRulesResponse response = leagueService.updateScoringRules(
                leagueId,
                commissionerId,
                Map.of("home_runs", 10.0, "rbi", 2.0));

        assertThat(response.pointValues().get("home_runs")).isEqualTo(10.0);
        assertThat(response.pointValues().get("rbi")).isEqualTo(2.0);
        assertThat(response.pointValues().get("stolen_bases")).isEqualTo(2.0);
        verify(scoringRuleRepository).save(any(ScoringRule.class));
    }

    @Test
    void updateScoringRulesRejectsNonCommissioner() {
        League league = league(LeagueVisibility.PRIVATE, 10);
        when(leagueRepository.findById(leagueId)).thenReturn(Optional.of(league));

        assertApiStatus(HttpStatus.FORBIDDEN, () -> leagueService.updateScoringRules(
                leagueId, memberId, Map.of("home_runs", 4.0)));
        verify(scoringRuleRepository, never()).save(any());
    }

    @Test
    void updateScoringRulesRejectsUnknownCategory() {
        League league = league(LeagueVisibility.PRIVATE, 10);
        when(leagueRepository.findById(leagueId)).thenReturn(Optional.of(league));

        assertApiStatus(HttpStatus.BAD_REQUEST, () -> leagueService.updateScoringRules(
                leagueId, commissionerId, Map.of("triples", 3.0)));
    }

    private League league(LeagueVisibility visibility, int maxMembers) {
        League league = new League("Test League", 2026, commissionerId);
        setField(league, "id", leagueId);
        league.setVisibility(visibility);
        league.setMaxMembers(maxMembers);
        league.setJoinCode("ORIGINAL1");
        return league;
    }

    private RosterSlot slot(UUID userId, int playerId) {
        return new RosterSlot(leagueId, userId, playerId, "UTIL", true);
    }

    private static Player playerWithSalary(int mlbId, String salary) {
        Player player = newInstance(Player.class);
        setField(player, "mlbId", mlbId);
        player.setSalary(new BigDecimal(salary));
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

    private static void assertApiStatus(HttpStatus expected, Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(expected));
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
