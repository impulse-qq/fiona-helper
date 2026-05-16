package io.promptforge.repository;

import io.promptforge.entity.CharacterEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class CharacterRepository implements PanacheRepository<CharacterEntity> {

    public Optional<CharacterEntity> findByIdOptional(UUID id) {
        return find("id", id).firstResultOptional();
    }
}
