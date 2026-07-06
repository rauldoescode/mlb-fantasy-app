package com.mlbfantasy.controller;

import com.mlbfantasy.dto.AddRosterPlayerRequest;
import com.mlbfantasy.dto.RosterResponse;
import com.mlbfantasy.dto.RosterSlotResponse;
import com.mlbfantasy.dto.UpdateRosterSlotRequest;
import com.mlbfantasy.security.AppUserPrincipal;
import com.mlbfantasy.service.RosterService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RosterController {

    private final RosterService rosterService;

    public RosterController(RosterService rosterService) {
        this.rosterService = rosterService;
    }

    @GetMapping("/api/leagues/{leagueId}/roster")
    public RosterResponse getRoster(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable UUID leagueId) {
        return rosterService.getRoster(leagueId, principal.getId());
    }

    @PostMapping("/api/leagues/{leagueId}/roster")
    public ResponseEntity<RosterSlotResponse> addPlayer(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable UUID leagueId,
            @Valid @RequestBody AddRosterPlayerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(rosterService.addPlayer(leagueId, principal.getId(), request));
    }

    @PatchMapping("/api/roster/{slotId}")
    public RosterSlotResponse updateSlot(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable UUID slotId,
            @RequestBody UpdateRosterSlotRequest request) {
        return rosterService.updateSlot(slotId, principal.getId(), request);
    }

    @DeleteMapping("/api/roster/{slotId}")
    public ResponseEntity<Void> removeSlot(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable UUID slotId) {
        rosterService.removeSlot(slotId, principal.getId());
        return ResponseEntity.noContent().build();
    }
}
