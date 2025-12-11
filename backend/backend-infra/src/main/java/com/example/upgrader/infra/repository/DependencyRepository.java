package com.example.upgrader.infra.repository;

import com.example.upgrader.infra.entity.Dependency;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DependencyRepository extends JpaRepository<Dependency, Long> {
}
