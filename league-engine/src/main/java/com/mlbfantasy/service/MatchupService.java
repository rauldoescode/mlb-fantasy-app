package com.mlbfantasy.service;

import com.mlbfantasy.dto.LineupPlayerCard;
import com.mlbfantasy.dto.MatchupDetailResponse;
import com.mlbfantasy.dto.MatchupLineupSide;
import com.mlbfantasy.dto.MatchupResponse;
import com.mlbfantasy.dto.PlayerGamePerformance;
import com.mlbfantasy.dto.PlayerWeekScore;
import com.mlbfantasy.dto.RosterSlotResponse;
import com.mlbfantasy.dto.ScoreBreakdown;
import com.mlbfantasy.dto.SetMatchupLineupRequest;
import com.mlbfantasy.dto.UpdateRosterSlotRequest;
import com.mlbfantasy.dto.WeekScoreResult;
import com.mlbfantasy.exception.ApiException;
import com.mlbfantasy.model.LeagueMember;
import com.mlbfantasy.model.LeagueMemberId;
import com.mlbfantasy.model.Matchup;
import com.mlbfantasy.model.MatchupPlayerScore;
import com.mlbfantasy.model.Player;
import com.mlbfantasy.model.RosterSlot;
import com.mlbfantasy.repository.LeagueMemberRepository;
import com.mlbfantasy.repository.MatchupPlayerScoreRepository;
import com.mlbfantasy.repository.MatchupRepository;
import com.mlbfantasy.repository.PlayerRepository;
import com.mlbfantasy.repository.RosterSlotRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MatchupService {

    private final MatchupRepository matchupRepository;
    private final LeagueMemberRepository leagueMemberRepository;
    private final RosterSlotRepository rosterSlotRepository;
    private final PlayerRepository playerRepository;
    private final MatchupPlayerScoreRepository matchupPlayerScoreRepository;
    private final LeagueService leagueService;
    private final ScoringService scoringService;
    private final LineupLockService lineupLockService;
    private final RosterService rosterService;
    private final PerformanceLockService performanceLockService;
    private final WeekService weekService;

    public MatchupService(MatchupRepository matchupRepository,
                          LeagueMemberRepository leagueMemberRepository,
                          RosterSlotRepository rosterSlotRepository,
                          PlayerRepository playerRepository,
                          MatchupPlayerScoreRepository matchupPlayerScoreRepository,
                          LeagueService leagueService,
                          ScoringService scoringService,
                          LineupLockService lineupLockService,
                          RosterService rosterService,
                          PerformanceLockService performanceLockService,
                          WeekService weekService) {
        this.matchupRepository = matchupRepository;
        this.leagueMemberRepository = leagueMemberRepository;
        this.rosterSlotRepository = rosterSlotRepository;
        this.playerRepository = playerRepository;
        this.matchupPlayerScoreRepository = matchupPlayerScoreRepository;
        this.leagueService = leagueService;
        this.scoringService = scoringService;
        this.lineupLockService = lineupLockService;
        this.rosterService = rosterService;
        this.performanceLockService = performanceLockService;
        this.weekService = weekService;
    }

    @Transactional
    public List<MatchupResponse> generateMatchups(UUID leagueId, UUID requesterId, int week) {
        leagueService.requireCommissioner(leagueId, requesterId);
        if (week < 1 || week > weekService.totalWeeks()) {
            throw ApiException.badRequest(
                    "Week must be between 1 and " + weekService.totalWeeks());
        }
        if (!matchupRepository.findByLeagueIdAndWeekNumber(leagueId, week).isEmpty()) {
            throw ApiException.conflict("Matchups for week " + week + " already exist");
        }

        List<UUID> members = sortedMembers(leagueId);
        if (members.size() < 2) {
            throw ApiException.badRequest("Need at least two members to schedule matchups");
        }

        List<Matchup> created = buildWeek(leagueId, week, members);
        matchupRepository.saveAll(created);
        return created.stream().map(MatchupResponse::from).toList();
    }

    @Transactional
    public List<MatchupResponse> getMatchups(UUID leagueId, UUID requesterId, Integer week) {
        leagueService.requireMember(leagueId, requesterId);
        ensureMatchupsThroughCurrentWeek(leagueId);
        List<Matchup> matchups = week == null
                ? matchupRepository.findByLeagueId(leagueId)
                : matchupRepository.findByLeagueIdAndWeekNumber(leagueId, week);
        return matchups.stream().map(MatchupResponse::from).toList();
    }

    /**
     * Idempotently generates the round-robin schedule for every week from 1 through the
     * current season week that does not already have matchups. Safe to call repeatedly;
     * existing weeks are never regenerated, so finalized results are preserved.
     */
    @Transactional
    public void ensureMatchupsThroughCurrentWeek(UUID leagueId) {
        List<UUID> members = sortedMembers(leagueId);
        if (members.size() < 2) {
            return; // Nothing to schedule until at least two teams have joined.
        }
        int throughWeek = weekService.currentSeasonWeek();
        Set<Integer> existingWeeks = matchupRepository.findByLeagueId(leagueId).stream()
                .map(Matchup::getWeekNumber)
                .collect(Collectors.toSet());

        List<Matchup> toCreate = new ArrayList<>();
        for (int week = 1; week <= throughWeek; week++) {
            if (!existingWeeks.contains(week)) {
                toCreate.addAll(buildWeek(leagueId, week, members));
            }
        }
        if (!toCreate.isEmpty()) {
            matchupRepository.saveAll(toCreate);
        }
    }

    private List<UUID> sortedMembers(UUID leagueId) {
        return leagueMemberRepository.findByIdLeagueId(leagueId).stream()
                .map(LeagueMember::getUserId)
                .sorted()
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private List<Matchup> buildWeek(UUID leagueId, int week, List<UUID> members) {
        return roundRobinPairs(members, week - 1).stream()
                .map(pair -> new Matchup(leagueId, week, pair[0], pair[1]))
                .collect(Collectors.toList());
    }

    /**
     * Standard "circle method" round-robin: team 0 stays fixed while the rest rotate by
     * {@code roundIndex}. With an odd number of teams a null bye is inserted, so the odd
     * team out simply gets no matchup row that week.
     */
    private List<UUID[]> roundRobinPairs(List<UUID> teamsInput, int roundIndex) {
        List<UUID> teams = new ArrayList<>(teamsInput);
        if (teams.size() % 2 != 0) {
            teams.add(null);
        }
        int n = teams.size();
        int rounds = n - 1;
        int r = ((roundIndex % rounds) + rounds) % rounds;

        List<UUID> rotating = new ArrayList<>(teams.subList(1, n));
        Collections.rotate(rotating, r);

        List<UUID> arranged = new ArrayList<>(n);
        arranged.add(teams.get(0));
        arranged.addAll(rotating);

        List<UUID[]> pairs = new ArrayList<>();
        for (int i = 0; i < n / 2; i++) {
            UUID a = arranged.get(i);
            UUID b = arranged.get(n - 1 - i);
            if (a != null && b != null) {
                pairs.add(new UUID[] {a, b});
            }
        }
        return pairs;
    }

    /**
     * Any league member may view any matchup in the league (Sleeper-style).
     * FINAL weeks read snapshot scores only; open weeks live-score and attach lineups.
     */
    @Transactional
    public MatchupDetailResponse getMatchupDetail(UUID matchupId, UUID requesterId) {
        Matchup matchup = requireMatchup(matchupId);
        leagueService.requireMember(matchup.getLeagueId(), requesterId);

        UUID leagueId = matchup.getLeagueId();
        WeekScoreResult oneResult;
        WeekScoreResult twoResult;

        if ("FINAL".equals(matchup.getStatus())) {
            oneResult = scoringService.scoreWeekFromSnapshot(matchupId, matchup.getUserOneId());
            twoResult = scoringService.scoreWeekFromSnapshot(matchupId, matchup.getUserTwoId());
            // Prefer persisted matchup totals when present so hero scores stay stable.
            oneResult = withStoredTotal(oneResult, matchup.getUserOneScore());
            twoResult = withStoredTotal(twoResult, matchup.getUserTwoScore());
        } else {
            // Materialize first-pitch freezes before scoring so eligibility is deterministic.
            materializeLocks(leagueId, matchup.getUserOneId());
            materializeLocks(leagueId, matchup.getUserTwoId());

            oneResult = scoringService.scoreWeekDetailed(
                    leagueId, matchup.getUserOneId(), matchup.getWeekNumber());
            twoResult = scoringService.scoreWeekDetailed(
                    leagueId, matchup.getUserTwoId(), matchup.getWeekNumber());

            matchup.setUserOneScore(oneResult.breakdown().totalPoints());
            matchup.setUserTwoScore(twoResult.breakdown().totalPoints());
            if ("SCHEDULED".equals(matchup.getStatus())
                    && matchup.getWeekNumber() <= weekService.currentWeek()) {
                matchup.setStatus("IN_PROGRESS");
            }
            matchup = matchupRepository.save(matchup);
        }

        MatchupLineupSide userOneLineup = buildLineupSide(
                leagueId, matchup.getUserOneId(), oneResult, "FINAL".equals(matchup.getStatus()));
        MatchupLineupSide userTwoLineup = buildLineupSide(
                leagueId, matchup.getUserTwoId(), twoResult, "FINAL".equals(matchup.getStatus()));

        return new MatchupDetailResponse(
                MatchupResponse.from(matchup),
                weekService.weekStart(matchup.getWeekNumber()),
                weekService.weekEnd(matchup.getWeekNumber()),
                oneResult.breakdown(),
                twoResult.breakdown(),
                userOneLineup,
                userTwoLineup,
                lineupLockService.isMatchupLineupEditable(matchup, requesterId));
    }

    /**
     * Start/Bench a player from the matchup card. Reuses roster update rules after
     * verifying the matchup is the current open week for a participant.
     */
    @Transactional
    public RosterSlotResponse setMatchupLineup(UUID matchupId,
                                               UUID slotId,
                                               UUID requesterId,
                                               SetMatchupLineupRequest request) {
        if (request == null || request.active() == null) {
            throw ApiException.badRequest("active is required");
        }
        Matchup matchup = requireMatchup(matchupId);
        leagueService.requireMember(matchup.getLeagueId(), requesterId);
        lineupLockService.requireMatchupLineupEditable(matchup, requesterId);

        RosterSlot slot = rosterSlotRepository.findById(slotId)
                .orElseThrow(() -> ApiException.notFound("Roster slot not found"));
        if (!matchup.getLeagueId().equals(slot.getLeagueId())) {
            throw ApiException.badRequest("Roster slot is not in this matchup's league");
        }

        return rosterService.updateSlot(
                slotId, requesterId, new UpdateRosterSlotRequest(request.active(), null));
    }

    @Transactional
    public MatchupResponse finalizeMatchup(UUID matchupId, UUID requesterId) {
        Matchup matchup = requireMatchup(matchupId);
        leagueService.requireCommissioner(matchup.getLeagueId(), requesterId);
        return finalizeMatchupInternal(matchup);
    }

    /**
     * Finalizes every open matchup whose week has fully ended ({@code weekEnd < today}
     * in the league zone). Idempotent for already-FINAL rows that already have snapshots.
     * Used by the weekly auto-finalize cron and safe for catch-up after downtime.
     */
    @Transactional
    public int finalizeCompletedWeeks(UUID leagueId) {
        LocalDate today = LocalDate.now(weekService.zone());
        int finalizedCount = 0;
        int throughWeek = weekService.currentSeasonWeek();
        for (int week = 1; week <= throughWeek; week++) {
            if (!weekService.weekEnd(week).isBefore(today)) {
                continue; // Week still in progress (or ends today) — wait for next run.
            }
            for (Matchup matchup : matchupRepository.findByLeagueIdAndWeekNumber(leagueId, week)) {
                if ("FINAL".equals(matchup.getStatus())
                        && matchupPlayerScoreRepository.existsByMatchupId(matchup.getId())) {
                    continue;
                }
                finalizeMatchupInternal(matchup);
                finalizedCount++;
            }
        }
        return finalizedCount;
    }

    /**
     * Core finalize: auto-lock best games, persist per-player snapshots, set winner
     * and {@code FINAL}. Safe to call repeatedly.
     */
    @Transactional
    public MatchupResponse finalizeMatchupInternal(Matchup matchup) {
        if ("FINAL".equals(matchup.getStatus())
                && matchupPlayerScoreRepository.existsByMatchupId(matchup.getId())) {
            return MatchupResponse.from(matchup);
        }

        UUID leagueId = matchup.getLeagueId();
        int week = matchup.getWeekNumber();
        UUID userOne = matchup.getUserOneId();
        UUID userTwo = matchup.getUserTwoId();

        materializeLocks(leagueId, userOne);
        materializeLocks(leagueId, userTwo);

        WeekScoreResult oneResult = scoringService.scoreWeekDetailed(leagueId, userOne, week);
        WeekScoreResult twoResult = scoringService.scoreWeekDetailed(leagueId, userTwo, week);

        autoLockBestGames(leagueId, week, userOne, oneResult);
        autoLockBestGames(leagueId, week, userTwo, twoResult);

        // Re-score so any newly auto-locked games are reflected in totals/snapshots.
        oneResult = scoringService.scoreWeekDetailed(leagueId, userOne, week);
        twoResult = scoringService.scoreWeekDetailed(leagueId, userTwo, week);

        matchupPlayerScoreRepository.deleteByMatchupId(matchup.getId());
        writePlayerSnapshots(matchup.getId(), userOne, oneResult);
        writePlayerSnapshots(matchup.getId(), userTwo, twoResult);

        matchup.setUserOneScore(oneResult.breakdown().totalPoints());
        matchup.setUserTwoScore(twoResult.breakdown().totalPoints());

        int cmp = oneResult.breakdown().totalPoints()
                .compareTo(twoResult.breakdown().totalPoints());
        if (cmp > 0) {
            matchup.setWinnerId(userOne);
        } else if (cmp < 0) {
            matchup.setWinnerId(userTwo);
        } else {
            matchup.setWinnerId(null); // Tie.
        }
        matchup.setStatus("FINAL");
        matchup.setFinalizedAt(OffsetDateTime.now());
        matchup = matchupRepository.save(matchup);
        return MatchupResponse.from(matchup);
    }

    private void autoLockBestGames(UUID leagueId,
                                   int week,
                                   UUID userId,
                                   WeekScoreResult weekScore) {
        for (PlayerWeekScore line : weekScore.playerScores()) {
            if (line.playerId() == null) {
                continue;
            }
            // Lock for anyone who contributed or was started — covers mid-week bench cases.
            if (line.slotActive() || line.scoringGamePk() != null) {
                performanceLockService.autoLockBestIfAbsent(
                        leagueId, week, userId, line.playerId());
            }
        }
    }

    private void writePlayerSnapshots(UUID matchupId, UUID userId, WeekScoreResult weekScore) {
        List<MatchupPlayerScore> rows = new ArrayList<>();
        for (PlayerWeekScore line : weekScore.playerScores()) {
            if (line.playerId() == null) {
                continue;
            }
            rows.add(new MatchupPlayerScore(
                    matchupId,
                    userId,
                    line.playerId(),
                    line.slotActive(),
                    line.points(),
                    line.scoringGamePk(),
                    line.categoryPoints()));
        }
        if (!rows.isEmpty()) {
            matchupPlayerScoreRepository.saveAll(rows);
        }
    }

    private void materializeLocks(UUID leagueId, UUID userId) {
        List<RosterSlot> slots = rosterSlotRepository.findByLeagueIdAndUserId(leagueId, userId);
        for (RosterSlot slot : slots) {
            if (slot.getPlayerId() != null) {
                lineupLockService.materializeTodaysLockIfNeeded(
                        leagueId, userId, slot.getPlayerId(), Boolean.TRUE.equals(slot.getActive()));
            }
        }
    }

    private MatchupLineupSide buildLineupSide(UUID leagueId,
                                              UUID userId,
                                              WeekScoreResult weekScore,
                                              boolean fromSnapshot) {
        String teamName = leagueMemberRepository.findById(new LeagueMemberId(leagueId, userId))
                .map(LeagueMember::getTeamName)
                .orElse("TBD");

        List<RosterSlot> slots = rosterSlotRepository.findByLeagueIdAndUserId(leagueId, userId);
        Map<Integer, RosterSlot> slotByPlayer = slots.stream()
                .filter(slot -> slot.getPlayerId() != null)
                .collect(Collectors.toMap(RosterSlot::getPlayerId, Function.identity(), (a, b) -> a));

        Map<Integer, PlayerWeekScore> scoreByPlayer = weekScore.playerScores().stream()
                .collect(Collectors.toMap(PlayerWeekScore::playerId, Function.identity(), (a, b) -> a));

        // Prefer roster order for open weeks; for FINAL snapshots include any scored players
        // even if they were later dropped from the live roster.
        List<Integer> playerIds = new ArrayList<>();
        if (fromSnapshot) {
            for (PlayerWeekScore score : weekScore.playerScores()) {
                if (score.playerId() != null && !playerIds.contains(score.playerId())) {
                    playerIds.add(score.playerId());
                }
            }
            for (Integer playerId : slotByPlayer.keySet()) {
                if (!playerIds.contains(playerId)) {
                    playerIds.add(playerId);
                }
            }
        } else {
            playerIds.addAll(slotByPlayer.keySet());
        }

        Map<Integer, Player> players = loadPlayers(playerIds);

        List<LineupPlayerCard> starters = new ArrayList<>();
        List<LineupPlayerCard> bench = new ArrayList<>();

        for (Integer playerId : playerIds) {
            RosterSlot slot = slotByPlayer.get(playerId);
            PlayerWeekScore score = scoreByPlayer.get(playerId);
            Player player = players.get(playerId);

            boolean active = score != null
                    ? score.slotActive()
                    : slot != null && Boolean.TRUE.equals(slot.getActive());
            boolean gameLocked = lineupLockService.isPlayerLocked(playerId);
            List<PlayerGamePerformance> games = score != null && score.games() != null
                    ? score.games()
                    : List.of();
            BigDecimal weekPoints = score != null
                    ? score.points()
                    : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            BigDecimal bestGamePoints = bestEligiblePoints(games);
            Integer scoringGamePk = score != null ? score.scoringGamePk() : null;
            boolean performanceLocked = score != null && score.performanceLocked();

            LineupPlayerCard card = new LineupPlayerCard(
                    slot != null ? slot.getId() : null,
                    playerId,
                    player != null ? player.getFullName() : null,
                    player != null ? player.getPrimaryPos() : null,
                    player != null ? player.getTeamAbbrev() : null,
                    active,
                    gameLocked,
                    weekPoints,
                    bestGamePoints,
                    scoringGamePk,
                    performanceLocked,
                    games);

            if (active) {
                starters.add(card);
            } else {
                bench.add(card);
            }
        }

        starters.sort(Comparator.comparing(c -> c.fullName() != null ? c.fullName() : "",
                String.CASE_INSENSITIVE_ORDER));
        bench.sort(Comparator.comparing(c -> c.fullName() != null ? c.fullName() : "",
                String.CASE_INSENSITIVE_ORDER));

        return new MatchupLineupSide(
                userId,
                teamName,
                List.copyOf(starters),
                List.copyOf(bench),
                weekScore.breakdown().totalPoints());
    }

    private Map<Integer, Player> loadPlayers(List<Integer> playerIds) {
        if (playerIds.isEmpty()) {
            return Map.of();
        }
        return playerRepository.findAllById(playerIds).stream()
                .collect(Collectors.toMap(Player::getMlbId, p -> p));
    }

    private static BigDecimal bestEligiblePoints(List<PlayerGamePerformance> games) {
        return games.stream()
                .filter(PlayerGamePerformance::eligible)
                .map(PlayerGamePerformance::points)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
    }

    private static WeekScoreResult withStoredTotal(WeekScoreResult result, BigDecimal storedTotal) {
        if (storedTotal == null) {
            return result;
        }
        ScoreBreakdown adjusted = new ScoreBreakdown(
                result.breakdown().userId(),
                storedTotal.setScale(2, RoundingMode.HALF_UP),
                result.breakdown().categoryPoints());
        return new WeekScoreResult(adjusted, result.playerScores());
    }

    private Matchup requireMatchup(UUID matchupId) {
        return matchupRepository.findById(matchupId)
                .orElseThrow(() -> ApiException.notFound("Matchup not found"));
    }
}
