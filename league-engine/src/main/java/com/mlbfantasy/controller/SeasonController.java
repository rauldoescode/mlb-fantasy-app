package com.mlbfantasy.controller;

import com.mlbfantasy.dto.SeasonResponse;
import com.mlbfantasy.service.WeekService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/season")
public class SeasonController {

    private final WeekService weekService;

    public SeasonController(WeekService weekService) {
        this.weekService = weekService;
    }

    @GetMapping
    public SeasonResponse season() {
        return new SeasonResponse(weekService.currentSeasonWeek(), weekService.totalWeeks());
    }
}
