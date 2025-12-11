package com.example.upgrader.infra.repository;

import com.example.upgrader.infra.entity.Change;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChangeRepository extends JpaRepository<Change, Long> {
}
