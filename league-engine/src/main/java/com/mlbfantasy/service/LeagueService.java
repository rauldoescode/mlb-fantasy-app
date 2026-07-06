package com.mlbfantasy.service;

import com.mlbfantasy.dto.AddMemberRequest;
import com.mlbfantasy.dto.CreateLeagueRequest;
import com.mlbfantasy.dto.LeagueResponse;
import com.mlbfantasy.dto.StandingRow;
import com.mlbfantasy.exception.ApiException;
import com.mlbfantasy.model.League;
import com.mlbfantasy.model.LeagueMember;
import com.mlbfantasy.model.Matchup;
import com.mlbfantasy.model.ScoringRule;
import com.mlbfantasy.model.User;
import com.mlbfantasy.repository.LeagueMemberRepository;
import com.mlbfantasy.repository.LeagueRepository;
import com.mlbfantasy.repository.MatchupRepository;
import com.mlbfantasy.repository.ScoringRuleRepository;
import com.mlbfantasy.repository.UserRepository;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LeagueService {

    private final LeagueRepository leagueRepository;
    private final LeagueMemberRepository leagueMemberRepository;
    private final ScoringRuleRepository scoringRuleRepository;
    private final MatchupRepository matchupRepository;
    private final UserRepository userRepository;

    public LeagueService(LeagueRepository leagueRepository,
                         LeagueMemberRepository leagueMemberRepository,
                         ScoringRuleRepository scoringRuleRepository,
                         MatchupRepository matchupRepository,
                         UserRepository userRepository) {
        this.leagueRepository = leagueRepository;
        this.leagueMemberRepository = leagueMemberRepository;
        this.scoringRuleRepository = scoringRuleRepository;
        this.matchupRepository = matchupRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public LeagueResponse createLeague(UUID commissionerId, CreateLeagueRequest request) {
        League league = new League(request.name(), request.seasonYear(), commissionerId);
        if (request.salaryCap() != null) {
            league.setSalaryCap(request.salaryCap());
        }
        if (request.rosterSize() != null) {
            league.setRosterSize(request.rosterSize());
        }
        league = leagueRepository.save(league);

        leagueMemberRepository.save(
                new LeagueMember(league.getId(), commissionerId, request.teamName()));
        scoringRuleRepository.save(
                new ScoringRule(league.getId(), new HashMap<>(ScoringService.DEFAULT_POINT_VALUES)));

        return LeagueResponse.from(league);
    }

    @Transactional(readOnly = true)
    public List<LeagueResponse> getLeaguesForUser(UUID userId) {
        return leagueRepository.findLeaguesForUser(userId).stream()
                .map(LeagueResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public LeagueResponse getLeague(UUID leagueId, UUID requesterId) {
        League league = requireLeague(leagueId);
        requireMember(leagueId, requesterId);
        return LeagueResponse.from(league);
    }

    @Transactional
    public void addMember(UUID leagueId, UUID requesterId, AddMemberRequest request) {
        requireCommissioner(leagueId, requesterId);
        User invitee = userRepository.findByEmail(request.email().toLowerCase())
                .orElseThrow(() -> ApiException.notFound("No user with that email"));
        if (leagueMemberRepository.existsByIdLeagueIdAndIdUserId(leagueId, invitee.getId())) {
            throw ApiException.conflict("User is already a member of this league");
        }
        leagueMemberRepository.save(
                new LeagueMember(leagueId, invitee.getId(), request.teamName()));
    }

    @Transactional
    public void updateScoringRules(UUID leagueId, UUID requesterId, Map<String, Double> pointValues) {
        requireCommissioner(leagueId, requesterId);
        ScoringRule rule = scoringRuleRepository.findById(leagueId)
                .orElseGet(() -> new ScoringRule(leagueId, new HashMap<>()));
        rule.setPointValues(new HashMap<>(pointValues));
        scoringRuleRepository.save(rule);
    }

    @Transactional(readOnly = true)
    public List<StandingRow> getStandings(UUID leagueId, UUID requesterId) {
        requireMember(leagueId, requesterId);
        List<LeagueMember> members = leagueMemberRepository.findByIdLeagueId(leagueId);
        List<Matchup> finals = matchupRepository.findByLeagueIdAndStatus(leagueId, "FINAL");

        Map<UUID, int[]> records = new HashMap<>(); // [wins, losses, ties]
        Map<UUID, BigDecimal> pointsFor = new HashMap<>();
        for (LeagueMember member : members) {
            records.put(member.getUserId(), new int[3]);
            pointsFor.put(member.getUserId(), BigDecimal.ZERO);
        }

        for (Matchup m : finals) {
            accumulate(records, pointsFor, m.getUserOneId(), m.getUserOneScore());
            accumulate(records, pointsFor, m.getUserTwoId(), m.getUserTwoScore());
            UUID winner = m.getWinnerId();
            if (winner == null) {
                bump(records, m.getUserOneId(), 2);
                bump(records, m.getUserTwoId(), 2);
            } else {
                UUID loser = winner.equals(m.getUserOneId()) ? m.getUserTwoId() : m.getUserOneId();
                bump(records, winner, 0);
                bump(records, loser, 1);
            }
        }

        return members.stream()
                .map(member -> {
                    UUID uid = member.getUserId();
                    int[] rec = records.getOrDefault(uid, new int[3]);
                    String displayName = userRepository.findById(uid)
                            .map(User::getDisplayName).orElse("Unknown");
                    return new StandingRow(uid, displayName, member.getTeamName(),
                            rec[0], rec[1], rec[2], pointsFor.getOrDefault(uid, BigDecimal.ZERO));
                })
                .sorted((a, b) -> {
                    int cmp = Integer.compare(b.wins(), a.wins());
                    return cmp != 0 ? cmp : b.pointsFor().compareTo(a.pointsFor());
                })
                .toList();
    }

    private void accumulate(Map<UUID, int[]> records, Map<UUID, BigDecimal> pointsFor,
                            UUID userId, BigDecimal score) {
        if (userId == null) {
            return;
        }
        pointsFor.merge(userId, score == null ? BigDecimal.ZERO : score, BigDecimal::add);
    }

    private void bump(Map<UUID, int[]> records, UUID userId, int index) {
        if (userId == null) {
            return;
        }
        records.computeIfAbsent(userId, k -> new int[3])[index]++;
    }

    public League requireLeague(UUID leagueId) {
        return leagueRepository.findById(leagueId)
                .orElseThrow(() -> ApiException.notFound("League not found"));
    }

    public void requireMember(UUID leagueId, UUID userId) {
        if (!leagueMemberRepository.existsByIdLeagueIdAndIdUserId(leagueId, userId)) {
            throw ApiException.forbidden("You are not a member of this league");
        }
    }

    public void requireCommissioner(UUID leagueId, UUID userId) {
        League league = requireLeague(leagueId);
        if (!userId.equals(league.getCommissionerId())) {
            throw ApiException.forbidden("Only the league commissioner can do that");
        }
    }
}
