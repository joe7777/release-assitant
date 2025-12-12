package com.example.upgrader.infra.repository;

import com.example.upgrader.core.model.Analysis;
import com.example.upgrader.core.repository.AnalysisRepository;
import com.example.upgrader.infra.mapper.AnalysisEntityMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
@Transactional
public class JpaAnalysisRepositoryAdapter implements AnalysisRepository {

    private final com.example.upgrader.infra.repository.AnalysisRepository analysisRepository;
    private final com.example.upgrader.infra.repository.ProjectRepository projectRepository;

    public JpaAnalysisRepositoryAdapter(com.example.upgrader.infra.repository.AnalysisRepository analysisRepository,
                                        com.example.upgrader.infra.repository.ProjectRepository projectRepository) {
        this.analysisRepository = analysisRepository;
        this.projectRepository = projectRepository;
    }

    @Override
    public Analysis save(Analysis analysis) {
        com.example.upgrader.infra.entity.Project project = projectRepository.findById(analysis.getProject().getId())
                .orElseThrow(() -> new IllegalArgumentException("Project not found for analysis"));
        com.example.upgrader.infra.entity.Analysis entity = analysis.getId() != null
                ? analysisRepository.findWithDetailsById(analysis.getId()).orElse(new com.example.upgrader.infra.entity.Analysis())
                : new com.example.upgrader.infra.entity.Analysis();

        AnalysisEntityMapper.copyToEntity(analysis, project, entity);
        entity.setTotalWorkpoints(analysis.getEffort() != null ? analysis.getEffort().getTotalWorkpoints() : null);

        return AnalysisEntityMapper.toModel(analysisRepository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Analysis> findById(Long id) {
        return analysisRepository.findWithDetailsById(id).map(AnalysisEntityMapper::toModel);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Analysis> findAll() {
        return analysisRepository.findAll().stream().map(AnalysisEntityMapper::toModel).toList();
    }
}
