package org.me.retrocoder.repository;

import org.me.retrocoder.model.Feature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Feature entities.
 * Note: Features are stored in project-specific databases (features.db).
 * This repository is used with dynamic datasource switching.
 */
@Repository
public interface FeatureRepository extends JpaRepository<Feature, Long> {

    /**
     * Find all features ordered by priority.
     */
    List<Feature> findAllByOrderByPriorityAsc();

    /**
     * Find pending features (not passing, not in progress).
     */
    List<Feature> findByPassesFalseAndInProgressFalseOrderByPriorityAsc();

    /**
     * Find in-progress features.
     */
    List<Feature> findByInProgressTrueOrderByPriorityAsc();

    /**
     * Find passing features.
     */
    List<Feature> findByPassesTrueOrderByPriorityAsc();

    /**
     * Find next pending feature (lowest priority).
     */
    Optional<Feature> findFirstByPassesFalseAndInProgressFalseOrderByPriorityAsc();

    /**
     * Count passing features.
     */
    long countByPassesTrue();

    /**
     * Count in-progress features.
     */
    long countByInProgressTrue();

    /**
     * Get max priority value.
     */
    @Query("SELECT COALESCE(MAX(f.priority), 0) FROM Feature f")
    int findMaxPriority();
}
