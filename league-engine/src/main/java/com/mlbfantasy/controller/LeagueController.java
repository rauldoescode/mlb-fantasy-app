package com.mlbfantasy.controller;

import com.mlbfantasy.dto.AddMemberRequest;
import com.mlbfantasy.dto.CreateLeagueRequest;
import com.mlbfantasy.dto.JoinByCodeRequest;
import com.mlbfantasy.dto.JoinLeagueRequest;
import com.mlbfantasy.dto.LeagueResponse;
import com.mlbfantasy.dto.PublicLeagueResponse;
import com.mlbfantasy.dto.ScoringRulesRequest;
import com.mlbfantasy.dto.StandingRow;
import com.mlbfantasy.dto.UpdateVisibilityRequest;
import com.mlbfantasy.security.AppUserPrincipal;
import com.mlbfantasy.service.LeagueService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/leagues")
public class LeagueController {

    private final LeagueService leagueService;

    public LeagueController(LeagueService leagueService) {
        this.leagueService = leagueService;
    }

    @PostMapping
    public ResponseEntity<LeagueResponse> create(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody CreateLeagueRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(leagueService.createLeague(principal.getId(), request));
    }

    @GetMapping
    public List<LeagueResponse> myLeagues(@AuthenticationPrincipal AppUserPrincipal principal) {
        return leagueService.getLeaguesForUser(principal.getId());
    }

    @GetMapping("/public")
    public List<PublicLeagueResponse> publicLeagues() {
        return leagueService.listPublicLeagues();
    }

    @PostMapping("/{leagueId}/join")
    public LeagueResponse join(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable UUID leagueId,
            @Valid @RequestBody JoinLeagueRequest request) {
        return leagueService.joinPublicLeague(leagueId, principal.getId(), request.teamName());
    }

    @PostMapping("/join-by-code")
    public LeagueResponse joinByCode(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody JoinByCodeRequest request) {
        return leagueService.joinLeagueByCode(
                request.joinCode(), principal.getId(), request.teamName());
    }

    @PostMapping("/{leagueId}/join-code/regenerate")
    public LeagueResponse regenerateJoinCode(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable UUID leagueId) {
        return leagueService.regenerateJoinCode(leagueId, principal.getId());
    }

    @PutMapping("/{leagueId}/visibility")
    public LeagueResponse updateVisibility(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable UUID leagueId,
            @Valid @RequestBody UpdateVisibilityRequest request) {
        return leagueService.updateVisibility(leagueId, principal.getId(), request.visibility());
    }

    @GetMapping("/{leagueId}")
    public LeagueResponse get(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable UUID leagueId) {
        return leagueService.getLeague(leagueId, principal.getId());
    }

    @PostMapping("/{leagueId}/members")
    public ResponseEntity<Void> addMember(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable UUID leagueId,
            @Valid @RequestBody AddMemberRequest request) {
        leagueService.addMember(leagueId, principal.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PutMapping("/{leagueId}/scoring-rules")
    public ResponseEntity<Void> updateScoringRules(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable UUID leagueId,
            @Valid @RequestBody ScoringRulesRequest request) {
        leagueService.updateScoringRules(leagueId, principal.getId(), request.pointValues());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{leagueId}/standings")
    public List<StandingRow> standings(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable UUID leagueId) {
        return leagueService.getStandings(leagueId, principal.getId());
    }
}
