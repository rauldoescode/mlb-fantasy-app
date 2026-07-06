package com.mlbfantasy.controller;

import com.mlbfantasy.dto.PlayerResponse;
import com.mlbfantasy.service.PlayerService;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/players")
public class PlayerController {

    private final PlayerService playerService;

    public PlayerController(PlayerService playerService) {
        this.playerService = playerService;
    }

    @GetMapping
    public Page<PlayerResponse> search(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String position,
            @RequestParam(required = false) String team,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return playerService.search(search, position, team, page, size);
    }

    @GetMapping("/{mlbId}")
    public PlayerResponse get(@PathVariable Integer mlbId) {
        return playerService.get(mlbId);
    }
}
