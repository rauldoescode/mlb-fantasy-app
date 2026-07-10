package com.mlbfantasy.service;

import com.mlbfantasy.dto.AddMemberRequest;
import com.mlbfantasy.dto.CreateLeagueRequest;
import com.mlbfantasy.dto.LeagueResponse;
import com.mlbfantasy.dto.PublicLeagueResponse;
import com.mlbfantasy.dto.StandingRow;
import com.mlbfantasy.exception.ApiException;
import com.mlbfantasy.model.League;
import com.mlbfantasy.model.LeagueMember;
import com.mlbfantasy.model.LeagueVisibility;
import com.mlbfantasy.model.Matchup;
import com.mlbfantasy.model.ScoringRule;
import com.mlbfantasy.model.User;
import com.mlbfantasy.repository.LeagueMemberRepository;
import com.mlbfantasy.repository.LeagueRepository;
import com.mlbfantasy.repository.MatchupRepository;
import com.mlbfantasy.repository.ScoringRuleRepository;
import com.mlbfantasy.repository.UserRepository;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LeagueService {

    /** Alphabet excludes ambiguous characters (0/O, 1/I/L). */
    private static final char[] JOIN_CODE_ALPHABET =
            "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int JOIN_CODE_LENGTH = 8;
    private static final int JOIN_CODE_MAX_ATTEMPTS = 10;
    private static final int DEFAULT_MAX_MEMBERS = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

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
        int maxMembers = request.maxMembers() != null ? request.maxMembers() : DEFAULT_MAX_MEMBERS;
        validateMaxMembers(maxMembers);

        League league = new League(request.name(), request.seasonYear(), commissionerId);
        if (request.salaryCap() != null) {
            league.setSalaryCap(request.salaryCap());
        }
        if (request.rosterSize() != null) {
            league.setRosterSize(request.rosterSize());
        }
        league.setVisibility(
                request.visibility() != null ? request.visibility() : LeagueVisibility.PRIVATE);
        league.setMaxMembers(maxMembers);
        league.setJoinCode(generateUniqueJoinCode());
        league = leagueRepository.save(league);

        leagueMemberRepository.save(
                new LeagueMember(league.getId(), commissionerId, request.teamName()));
        scoringRuleRepository.save(
                new ScoringRule(league.getId(), new HashMap<>(ScoringService.DEFAULT_POINT_VALUES)));

        return LeagueResponse.from(league, commissionerId, 1);
    }

    @Transactional(readOnly = true)
    public List<LeagueResponse> getLeaguesForUser(UUID userId) {
        return leagueRepository.findLeaguesForUser(userId).stream()
                .map(league -> LeagueResponse.from(
                        league, userId, leagueMemberRepository.countByIdLeagueId(league.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public LeagueResponse getLeague(UUID leagueId, UUID requesterId) {
        League league = requireLeague(leagueId);
        requireMember(leagueId, requesterId);
        return LeagueResponse.from(
                league, requesterId, leagueMemberRepository.countByIdLeagueId(leagueId));
    }

    @Transactional(readOnly = true)
    public List<PublicLeagueResponse> listPublicLeagues() {
        return leagueRepository.findByVisibilityOrderByCreatedAtDesc(LeagueVisibility.PUBLIC).stream()
                .map(league -> {
                    String commissioner = league.getCommissionerId() == null ? "Unknown"
                            : userRepository.findById(league.getCommissionerId())
                                    .map(User::getDisplayName).orElse("Unknown");
                    return new PublicLeagueResponse(
                            league.getId(),
                            league.getLeagueName(),
                            league.getSeasonYear(),
                            commissioner,
                            leagueMemberRepository.countByIdLeagueId(league.getId()),
                            league.getMaxMembers());
                })
                .toList();
    }

    @Transactional
    public LeagueResponse joinPublicLeague(UUID leagueId, UUID userId, String teamName) {
        League league = requireLeague(leagueId);
        if (league.getVisibility() != LeagueVisibility.PUBLIC) {
            throw ApiException.badRequest("This league is private; a join code is required");
        }
        return join(league, userId, teamName);
    }

    @Transactional
    public LeagueResponse joinLeagueByCode(String joinCode, UUID userId, String teamName) {
        League league = leagueRepository.findByJoinCode(joinCode)
                .orElseThrow(() -> ApiException.notFound("No league matches that join code"));
        return join(league, userId, teamName);
    }

    private LeagueResponse join(League league, UUID userId, String teamName) {
        UUID leagueId = league.getId();
        if (leagueMemberRepository.existsByIdLeagueIdAndIdUserId(leagueId, userId)) {
            throw ApiException.conflict("You are already a member of this league");
        }
        requireCapacity(league);
        leagueMemberRepository.save(new LeagueMember(leagueId, userId, teamName));
        return LeagueResponse.from(
                league, userId, leagueMemberRepository.countByIdLeagueId(leagueId));
    }

    @Transactional
    public LeagueResponse regenerateJoinCode(UUID leagueId, UUID requesterId) {
        requireCommissioner(leagueId, requesterId);
        League league = requireLeague(leagueId);
        league.setJoinCode(generateUniqueJoinCode());
        league = leagueRepository.save(league);
        return LeagueResponse.from(
                league, requesterId, leagueMemberRepository.countByIdLeagueId(leagueId));
    }

    @Transactional
    public LeagueResponse updateVisibility(UUID leagueId, UUID requesterId,
                                           LeagueVisibility visibility) {
        requireCommissioner(leagueId, requesterId);
        League league = requireLeague(leagueId);
        league.setVisibility(visibility);
        league = leagueRepository.save(league);
        return LeagueResponse.from(
                league, requesterId, leagueMemberRepository.countByIdLeagueId(leagueId));
    }

    @Transactional
    public void addMember(UUID leagueId, UUID requesterId, AddMemberRequest request) {
        requireCommissioner(leagueId, requesterId);
        League league = requireLeague(leagueId);
        User invitee = userRepository.findByEmail(request.email().toLowerCase())
                .orElseThrow(() -> ApiException.notFound("No user with that email"));
        if (leagueMemberRepository.existsByIdLeagueIdAndIdUserId(leagueId, invitee.getId())) {
            throw ApiException.conflict("User is already a member of this league");
        }
        requireCapacity(league);
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

    private void requireCapacity(League league) {
        if (leagueMemberRepository.countByIdLeagueId(league.getId()) >= league.getMaxMembers()) {
            throw ApiException.conflict("League is full");
        }
    }

    private void validateMaxMembers(int maxMembers) {
        if (maxMembers < 2 || maxMembers > 12 || maxMembers % 2 != 0) {
            throw ApiException.badRequest("maxMembers must be an even number between 2 and 12");
        }
    }

    private String generateUniqueJoinCode() {
        for (int attempt = 0; attempt < JOIN_CODE_MAX_ATTEMPTS; attempt++) {
            String code = randomCode();
            if (leagueRepository.findByJoinCode(code).isEmpty()) {
                return code;
            }
        }
        throw ApiException.conflict("Could not generate a unique join code, please retry");
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(JOIN_CODE_LENGTH);
        for (int i = 0; i < JOIN_CODE_LENGTH; i++) {
            sb.append(JOIN_CODE_ALPHABET[RANDOM.nextInt(JOIN_CODE_ALPHABET.length)]);
        }
        return sb.toString();
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
