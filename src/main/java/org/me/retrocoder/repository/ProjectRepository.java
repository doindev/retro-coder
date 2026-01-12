package org.me.retrocoder.repository;

import org.me.retrocoder.model.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Project entities in the registry database.
 */
@Repository
public interface ProjectRepository extends JpaRepository<Project, String> {

    /**
     * Find project by name.
     */
    Optional<Project> findByName(String name);

    /**
     * Check if project exists by name.
     */
    boolean existsByName(String name);

    /**
     * Find all projects ordered by creation date descending.
     */
    List<Project> findAllByOrderByCreatedAtDesc();

    /**
     * Find projects by path.
     */
    Optional<Project> findByPath(String path);
}
