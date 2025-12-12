package com.example.upgrader.infra.mapper;

import com.example.upgrader.core.model.Project;
import com.example.upgrader.infra.entity.Project;

public final class ProjectEntityMapper {

    private ProjectEntityMapper() {
    }

    public static Project toModel(com.example.upgrader.infra.entity.Project entity) {
        if (entity == null) {
            return null;
        }
        Project model = new Project();
        model.setId(entity.getId());
        model.setName(entity.getName());
        model.setGitUrl(entity.getGitUrl());
        model.setBranch(entity.getBranch());
        model.setGitTokenId(entity.getGitTokenId());
        return model;
    }

    public static com.example.upgrader.infra.entity.Project toEntity(Project model) {
        if (model == null) {
            return null;
        }
        com.example.upgrader.infra.entity.Project entity = new com.example.upgrader.infra.entity.Project();
        entity.setId(model.getId());
        entity.setName(model.getName());
        entity.setGitUrl(model.getGitUrl());
        entity.setBranch(model.getBranch());
        entity.setGitTokenId(model.getGitTokenId());
        return entity;
    }
}
