package com.mlbfantasy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LeagueEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(LeagueEngineApplication.class, args);
    }
}
