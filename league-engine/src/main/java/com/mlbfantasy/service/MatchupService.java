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
import java.util.UUID;
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
        if (!matchupRepository.findByLeagueIdAndWeekNumber(leagueId, week).isEmpty()) {
            throw ApiException.conflict("Matchups for week " + week + " already exist");
        }

        List<UUID> userIds = new ArrayList<>(
                leagueMemberRepository.findByIdLeagueId(leagueId).stream()
                        .map(LeagueMember::getUserId)
                        .toList());
        if (userIds.size() < 2) {
            throw ApiException.badRequest("Need at least two members to schedule matchups");
        }
        Collections.shuffle(userIds);

        List<Matchup> created = new ArrayList<>();
        for (int i = 0; i + 1 < userIds.size(); i += 2) {
            created.add(new Matchup(leagueId, week, userIds.get(i), userIds.get(i + 1)));
        }
        // Odd member out gets a bye this week (no matchup row).
        matchupRepository.saveAll(created);
        return created.stream().map(MatchupResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<MatchupResponse> getMatchups(UUID leagueId, UUID requesterId, Integer week) {
        leagueService.requireMember(leagueId, requesterId);
        List<Matchup> matchups = week == null
                ? matchupRepository.findByLeagueId(leagueId)
                : matchupRepository.findByLeagueIdAndWeekNumber(leagueId, week);
        return matchups.stream().map(MatchupResponse::from).toList();
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
