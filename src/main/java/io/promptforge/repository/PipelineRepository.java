package io.promptforge.repository;

import io.promptforge.entity.PipelineEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PipelineRepository implements PanacheRepository<PipelineEntity> {

    public Optional<PipelineEntity> findByIdOptional(UUID id) {
        return find("id", id).firstResultOptional();
    }

    public List<PipelineEntity> listAllOrdered() {
        return listAll();
    }
}
