package com.example.upgrader.infra.repository;

import com.example.upgrader.infra.entity.EffortSummary;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EffortSummaryRepository extends JpaRepository<EffortSummary, Long> {
}
