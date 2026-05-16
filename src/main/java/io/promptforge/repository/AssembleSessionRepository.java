package io.promptforge.repository;

import io.promptforge.entity.AssembleSessionEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class AssembleSessionRepository implements PanacheRepository<AssembleSessionEntity> {

    public Optional<AssembleSessionEntity> findByIdOptional(UUID id) {
        return find("id", id).firstResultOptional();
    }
}
