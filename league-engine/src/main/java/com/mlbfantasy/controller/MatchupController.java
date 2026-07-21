package com.mlbfantasy.controller;

import com.mlbfantasy.dto.MatchupDetailResponse;
import com.mlbfantasy.dto.MatchupResponse;
import com.mlbfantasy.dto.RosterSlotResponse;
import com.mlbfantasy.dto.SetMatchupLineupRequest;
import com.mlbfantasy.security.AppUserPrincipal;
import com.mlbfantasy.service.MatchupService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MatchupController {

    private final MatchupService matchupService;

    public MatchupController(MatchupService matchupService) {
        this.matchupService = matchupService;
    }

    @GetMapping("/api/leagues/{leagueId}/matchups")
    public List<MatchupResponse> getMatchups(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable UUID leagueId,
            @RequestParam(required = false) Integer week) {
        return matchupService.getMatchups(leagueId, principal.getId(), week);
    }

    @PostMapping("/api/leagues/{leagueId}/matchups/generate")
    public ResponseEntity<List<MatchupResponse>> generate(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable UUID leagueId,
            @RequestParam int week) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(matchupService.generateMatchups(leagueId, principal.getId(), week));
    }

    @GetMapping("/api/matchups/{matchupId}")
    public MatchupDetailResponse getDetail(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable UUID matchupId) {
        return matchupService.getMatchupDetail(matchupId, principal.getId());
    }

    @PatchMapping("/api/matchups/{matchupId}/lineup/{slotId}")
    public RosterSlotResponse setLineup(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable UUID matchupId,
            @PathVariable UUID slotId,
            @Valid @RequestBody SetMatchupLineupRequest request) {
        return matchupService.setMatchupLineup(matchupId, slotId, principal.getId(), request);
    }

    @PostMapping("/api/matchups/{matchupId}/finalize")
    public MatchupResponse finalize(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable UUID matchupId) {
        return matchupService.finalizeMatchup(matchupId, principal.getId());
    }
}
