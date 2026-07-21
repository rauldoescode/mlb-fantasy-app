package com.mlbfantasy.controller;

import com.mlbfantasy.dto.LockPerformanceRequest;
import com.mlbfantasy.dto.PerformanceLockResponse;
import com.mlbfantasy.security.AppUserPrincipal;
import com.mlbfantasy.service.PerformanceLockService;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PerformanceLockController {

    private final PerformanceLockService performanceLockService;

    public PerformanceLockController(PerformanceLockService performanceLockService) {
        this.performanceLockService = performanceLockService;
    }

    @PostMapping("/api/leagues/{leagueId}/weeks/{weekNumber}/players/{playerId}/lock-performance")
    public PerformanceLockResponse lock(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable UUID leagueId,
            @PathVariable int weekNumber,
            @PathVariable Integer playerId,
            @RequestBody(required = false) LockPerformanceRequest request) {
        return performanceLockService.lockPerformance(
                leagueId, weekNumber, playerId, principal.getId(), request);
    }

    @DeleteMapping("/api/leagues/{leagueId}/weeks/{weekNumber}/players/{playerId}/lock-performance")
    public ResponseEntity<Void> unlock(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable UUID leagueId,
            @PathVariable int weekNumber,
            @PathVariable Integer playerId) {
        performanceLockService.unlockPerformance(
                leagueId, weekNumber, playerId, principal.getId());
        return ResponseEntity.noContent().build();
    }
}
