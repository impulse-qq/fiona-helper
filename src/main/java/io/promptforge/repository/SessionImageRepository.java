package io.promptforge.repository;

import io.promptforge.entity.SessionImageEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class SessionImageRepository implements PanacheRepository<SessionImageEntity> {

    public List<SessionImageEntity> findBySessionId(UUID sessionId) {
        return find("sessionId", sessionId).list();
    }
}
