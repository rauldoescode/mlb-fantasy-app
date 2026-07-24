package com.mlbfantasy.service;

import com.mlbfantasy.dto.AddMemberRequest;
import com.mlbfantasy.dto.CreateLeagueRequest;
import com.mlbfantasy.dto.LeagueResponse;
import com.mlbfantasy.dto.PublicLeagueResponse;
import com.mlbfantasy.dto.ScoringRulesResponse;
import com.mlbfantasy.dto.StandingRow;
import com.mlbfantasy.dto.UpdateLeagueSettingsRequest;
import com.mlbfantasy.exception.ApiException;
import com.mlbfantasy.model.League;
import com.mlbfantasy.model.LeagueMember;
import com.mlbfantasy.model.LeagueVisibility;
import com.mlbfantasy.model.Matchup;
import com.mlbfantasy.model.Player;
import com.mlbfantasy.model.RosterSlot;
import com.mlbfantasy.model.ScoringRule;
import com.mlbfantasy.model.User;
import com.mlbfantasy.repository.LeagueMemberRepository;
import com.mlbfantasy.repository.LeagueRepository;
import com.mlbfantasy.repository.MatchupRepository;
import com.mlbfantasy.repository.PlayerRepository;
import com.mlbfantasy.repository.RosterSlotRepository;
import com.mlbfantasy.repository.ScoringRuleRepository;
import com.mlbfantasy.repository.UserRepository;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
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
    private static final int MIN_ROSTER_SIZE = 5;
    private static final int MAX_ROSTER_SIZE = 15;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final LeagueRepository leagueRepository;
    private final LeagueMemberRepository leagueMemberRepository;
    private final ScoringRuleRepository scoringRuleRepository;
    private final MatchupRepository matchupRepository;
    private final UserRepository userRepository;
    private final RosterSlotRepository rosterSlotRepository;
    private final PlayerRepository playerRepository;

    public LeagueService(LeagueRepository leagueRepository,
                         LeagueMemberRepository leagueMemberRepository,
                         ScoringRuleRepository scoringRuleRepository,
                         MatchupRepository matchupRepository,
                         UserRepository userRepository,
                         RosterSlotRepository rosterSlotRepository,
                         PlayerRepository playerRepository) {
        this.leagueRepository = leagueRepository;
        this.leagueMemberRepository = leagueMemberRepository;
        this.scoringRuleRepository = scoringRuleRepository;
        this.matchupRepository = matchupRepository;
        this.userRepository = userRepository;
        this.rosterSlotRepository = rosterSlotRepository;
        this.playerRepository = playerRepository;
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
    public LeagueResponse updateSettings(UUID leagueId, UUID requesterId,
                                         UpdateLeagueSettingsRequest request) {
        requireCommissioner(leagueId, requesterId);
        League league = requireLeague(leagueId);

        if (request.name() != null) {
            String trimmed = request.name().trim();
            if (trimmed.isEmpty()) {
                throw ApiException.badRequest("League name cannot be blank");
            }
            if (trimmed.length() > 60) {
                throw ApiException.badRequest("League name must be 60 characters or fewer");
            }
            league.setLeagueName(trimmed);
        }
        if (request.salaryCap() != null) {
            validateSalaryCapLowering(leagueId, request.salaryCap());
            league.setSalaryCap(request.salaryCap());
        }
        if (request.rosterSize() != null) {
            validateRosterSize(request.rosterSize());
            validateRosterSizeLowering(leagueId, request.rosterSize());
            league.setRosterSize(request.rosterSize());
        }
        if (request.maxMembers() != null) {
            validateMaxMembers(request.maxMembers());
            long members = leagueMemberRepository.countByIdLeagueId(leagueId);
            if (members > request.maxMembers()) {
                throw ApiException.conflict(
                        "Cannot set max members below current membership (" + members + ")");
            }
            league.setMaxMembers(request.maxMembers());
        }

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

    @Transactional(readOnly = true)
    public ScoringRulesResponse getScoringRules(UUID leagueId, UUID requesterId) {
        requireMember(leagueId, requesterId);
        Map<String, Double> stored = scoringRuleRepository.findById(leagueId)
                .map(ScoringRule::getPointValues)
                .orElse(Map.of());
        return new ScoringRulesResponse(leagueId, resolvePointValues(stored));
    }

    @Transactional
    public ScoringRulesResponse updateScoringRules(UUID leagueId, UUID requesterId,
                                                   Map<String, Double> pointValues) {
        requireCommissioner(leagueId, requesterId);
        if (pointValues == null || pointValues.isEmpty()) {
            throw ApiException.badRequest("pointValues cannot be empty");
        }
        Set<String> allowed = Set.copyOf(ScoringService.SCORING_CATEGORIES);
        for (Map.Entry<String, Double> entry : pointValues.entrySet()) {
            if (!allowed.contains(entry.getKey())) {
                throw ApiException.badRequest("Unknown scoring category: " + entry.getKey());
            }
            if (entry.getValue() == null || entry.getValue().isNaN() || entry.getValue().isInfinite()) {
                throw ApiException.badRequest("Invalid point value for " + entry.getKey());
            }
        }

        Map<String, Double> normalized = resolvePointValues(pointValues);
        ScoringRule rule = scoringRuleRepository.findById(leagueId)
                .orElseGet(() -> new ScoringRule(leagueId, new HashMap<>()));
        rule.setPointValues(new HashMap<>(normalized));
        scoringRuleRepository.save(rule);
        return new ScoringRulesResponse(leagueId, normalized);
    }

    /** Fills every known category in stable order; missing keys use defaults (or 0). */
    private static Map<String, Double> resolvePointValues(Map<String, Double> stored) {
        Map<String, Double> resolved = new LinkedHashMap<>();
        for (String key : ScoringService.SCORING_CATEGORIES) {
            Double value = stored != null ? stored.get(key) : null;
            if (value == null) {
                value = ScoringService.DEFAULT_POINT_VALUES.getOrDefault(key, 0.0);
            }
            resolved.put(key, value);
        }
        return resolved;
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

    private void validateRosterSize(int rosterSize) {
        if (rosterSize < MIN_ROSTER_SIZE || rosterSize > MAX_ROSTER_SIZE) {
            throw ApiException.badRequest("rosterSize must be between 5 and 15");
        }
    }

    private void validateRosterSizeLowering(UUID leagueId, int rosterSize) {
        List<RosterSlot> slots = rosterSlotRepository.findByLeagueId(leagueId);
        Map<UUID, Long> counts = slots.stream()
                .collect(Collectors.groupingBy(RosterSlot::getUserId, Collectors.counting()));
        long largest = counts.values().stream().mapToLong(Long::longValue).max().orElse(0L);
        if (largest > rosterSize) {
            throw ApiException.conflict(
                    "Cannot set roster size below the largest current roster (" + largest + ")");
        }
    }

    private void validateSalaryCapLowering(UUID leagueId, BigDecimal salaryCap) {
        List<RosterSlot> slots = rosterSlotRepository.findByLeagueId(leagueId);
        if (slots.isEmpty()) {
            return;
        }
        List<Integer> playerIds = slots.stream()
                .map(RosterSlot::getPlayerId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Integer, BigDecimal> salaries = playerRepository.findAllById(playerIds).stream()
                .collect(Collectors.toMap(
                        Player::getMlbId,
                        p -> p.getSalary() == null ? BigDecimal.ZERO : p.getSalary()));

        Map<UUID, BigDecimal> totals = new HashMap<>();
        for (RosterSlot slot : slots) {
            BigDecimal salary = slot.getPlayerId() == null
                    ? BigDecimal.ZERO
                    : salaries.getOrDefault(slot.getPlayerId(), BigDecimal.ZERO);
            totals.merge(slot.getUserId(), salary, BigDecimal::add);
        }
        boolean anyOver = totals.values().stream()
                .anyMatch(total -> total.compareTo(salaryCap) > 0);
        if (anyOver) {
            throw ApiException.conflict(
                    "Cannot set salary cap below a team's current roster salary");
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
