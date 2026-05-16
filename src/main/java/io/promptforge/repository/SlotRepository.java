package io.promptforge.repository;

import io.promptforge.entity.SlotEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class SlotRepository implements PanacheRepository<SlotEntity> {

    public List<SlotEntity> findByPipelineId(UUID pipelineId) {
        return find("pipeline.id = ?1 ORDER BY orderIndex ASC", pipelineId).list();
    }
}
