package com.mlbfantasy.repository;

import com.mlbfantasy.model.ScoringRule;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScoringRuleRepository extends JpaRepository<ScoringRule, UUID> {
}
