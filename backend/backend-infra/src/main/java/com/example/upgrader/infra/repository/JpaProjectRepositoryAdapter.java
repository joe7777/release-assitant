package com.example.upgrader.infra.repository;

import com.example.upgrader.core.model.Project;
import com.example.upgrader.core.repository.ProjectRepository;
import com.example.upgrader.infra.mapper.ProjectEntityMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
@Transactional
public class JpaProjectRepositoryAdapter implements ProjectRepository {

    private final com.example.upgrader.infra.repository.ProjectRepository projectRepository;

    public JpaProjectRepositoryAdapter(com.example.upgrader.infra.repository.ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Project> findByGitUrlAndBranch(String gitUrl, String branch) {
        return projectRepository.findByGitUrlAndBranch(gitUrl, branch).map(ProjectEntityMapper::toModel);
    }

    @Override
    public Project save(Project project) {
        com.example.upgrader.infra.entity.Project entity = project.getId() != null
                ? projectRepository.findById(project.getId()).orElse(new com.example.upgrader.infra.entity.Project())
                : new com.example.upgrader.infra.entity.Project();
        entity.setName(project.getName());
        entity.setGitUrl(project.getGitUrl());
        entity.setBranch(project.getBranch());
        entity.setGitTokenId(project.getGitTokenId());
        com.example.upgrader.infra.entity.Project saved = projectRepository.save(entity);
        return ProjectEntityMapper.toModel(saved);
    }
}
