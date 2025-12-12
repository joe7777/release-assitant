package com.example.upgrader.core.repository;

import com.example.upgrader.core.model.Analysis;

import java.util.List;
import java.util.Optional;

public interface AnalysisRepository {
    Analysis save(Analysis analysis);

    Optional<Analysis> findById(Long id);

    List<Analysis> findAll();
}
