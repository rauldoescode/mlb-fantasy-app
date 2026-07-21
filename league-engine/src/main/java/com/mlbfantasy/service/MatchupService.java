package com.mlbfantasy.service;

import com.mlbfantasy.dto.MatchupDetailResponse;
import com.mlbfantasy.dto.MatchupResponse;
import com.mlbfantasy.dto.ScoreBreakdown;
import com.mlbfantasy.exception.ApiException;
import com.mlbfantasy.model.LeagueMember;
import com.mlbfantasy.model.Matchup;
import com.mlbfantasy.repository.LeagueMemberRepository;
import com.mlbfantasy.repository.MatchupRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MatchupService {

    private final MatchupRepository matchupRepository;
    private final LeagueMemberRepository leagueMemberRepository;
    private final LeagueService leagueService;
    private final ScoringService scoringService;
    private final WeekService weekService;

    public MatchupService(MatchupRepository matchupRepository,
                          LeagueMemberRepository leagueMemberRepository,
                          LeagueService leagueService,
                          ScoringService scoringService,
                          WeekService weekService) {
        this.matchupRepository = matchupRepository;
        this.leagueMemberRepository = leagueMemberRepository;
        this.leagueService = leagueService;
        this.scoringService = scoringService;
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

    @Transactional
    public MatchupDetailResponse getMatchupDetail(UUID matchupId, UUID requesterId) {
        Matchup matchup = requireMatchup(matchupId);
        leagueService.requireMember(matchup.getLeagueId(), requesterId);

        ScoreBreakdown one = scoringService.scoreWeek(
                matchup.getLeagueId(), matchup.getUserOneId(), matchup.getWeekNumber());
        ScoreBreakdown two = scoringService.scoreWeek(
                matchup.getLeagueId(), matchup.getUserTwoId(), matchup.getWeekNumber());

        // Keep live scores fresh on the stored row until the matchup is finalized.
        if (!"FINAL".equals(matchup.getStatus())) {
            matchup.setUserOneScore(one.totalPoints());
            matchup.setUserTwoScore(two.totalPoints());
            if ("SCHEDULED".equals(matchup.getStatus())
                    && matchup.getWeekNumber() <= weekService.currentWeek()) {
                matchup.setStatus("IN_PROGRESS");
            }
            matchup = matchupRepository.save(matchup);
        }

        return new MatchupDetailResponse(
                MatchupResponse.from(matchup),
                weekService.weekStart(matchup.getWeekNumber()),
                weekService.weekEnd(matchup.getWeekNumber()),
                one,
                two);
    }

    @Transactional
    public MatchupResponse finalizeMatchup(UUID matchupId, UUID requesterId) {
        Matchup matchup = requireMatchup(matchupId);
        leagueService.requireCommissioner(matchup.getLeagueId(), requesterId);

        ScoreBreakdown one = scoringService.scoreWeek(
                matchup.getLeagueId(), matchup.getUserOneId(), matchup.getWeekNumber());
        ScoreBreakdown two = scoringService.scoreWeek(
                matchup.getLeagueId(), matchup.getUserTwoId(), matchup.getWeekNumber());

        matchup.setUserOneScore(one.totalPoints());
        matchup.setUserTwoScore(two.totalPoints());

        int cmp = one.totalPoints().compareTo(two.totalPoints());
        if (cmp > 0) {
            matchup.setWinnerId(matchup.getUserOneId());
        } else if (cmp < 0) {
            matchup.setWinnerId(matchup.getUserTwoId());
        } else {
            matchup.setWinnerId(null); // Tie.
        }
        matchup.setStatus("FINAL");
        matchup = matchupRepository.save(matchup);
        return MatchupResponse.from(matchup);
    }

    private Matchup requireMatchup(UUID matchupId) {
        return matchupRepository.findById(matchupId)
                .orElseThrow(() -> ApiException.notFound("Matchup not found"));
    }
}
