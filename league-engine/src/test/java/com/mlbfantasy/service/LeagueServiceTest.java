package com.mlbfantasy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mlbfantasy.dto.CreateLeagueRequest;
import com.mlbfantasy.exception.ApiException;
import com.mlbfantasy.model.League;
import com.mlbfantasy.model.LeagueMember;
import com.mlbfantasy.model.LeagueVisibility;
import com.mlbfantasy.repository.LeagueMemberRepository;
import com.mlbfantasy.repository.LeagueRepository;
import com.mlbfantasy.repository.MatchupRepository;
import com.mlbfantasy.repository.ScoringRuleRepository;
import com.mlbfantasy.repository.UserRepository;
import java.lang.reflect.Field;
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

    private LeagueService leagueService;

    private final UUID commissionerId = UUID.randomUUID();
    private final UUID memberId = UUID.randomUUID();
    private final UUID leagueId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        leagueService = new LeagueService(leagueRepository, leagueMemberRepository,
                scoringRuleRepository, matchupRepository, userRepository);
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

    private League league(LeagueVisibility visibility, int maxMembers) {
        League league = new League("Test League", 2026, commissionerId);
        setField(league, "id", leagueId);
        league.setVisibility(visibility);
        league.setMaxMembers(maxMembers);
        league.setJoinCode("ORIGINAL1");
        return league;
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
