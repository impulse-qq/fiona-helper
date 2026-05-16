package io.promptforge.repository;

import io.promptforge.entity.SlotDraftEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class SlotDraftRepository implements PanacheRepository<SlotDraftEntity> {

    public List<SlotDraftEntity> findBySessionId(UUID sessionId) {
        return find("sessionId", sessionId).list();
    }

    public Optional<SlotDraftEntity> findBySessionIdAndSlotId(UUID sessionId, UUID slotId) {
        return find("sessionId = ?1 AND slotId = ?2", sessionId, slotId).firstResultOptional();
    }
}
