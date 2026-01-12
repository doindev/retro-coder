package org.me.retrocoder.repository;

import org.me.retrocoder.model.Settings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for Settings entities in the registry database.
 */
@Repository
public interface SettingsRepository extends JpaRepository<Settings, String> {

    /**
     * Find setting by key.
     */
    Optional<Settings> findByKey(String key);

    /**
     * Check if setting exists by key.
     */
    boolean existsByKey(String key);
}
