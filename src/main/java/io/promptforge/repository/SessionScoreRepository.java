package io.promptforge.repository;

import io.promptforge.entity.SessionScoreEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class SessionScoreRepository implements PanacheRepository<SessionScoreEntity> {

    /** 按 (session, createdBy) 查询。用于 submitScore 时区分 update vs insert。 */
    public Optional<SessionScoreEntity> findBySessionIdAndCreatedBy(UUID sessionId, String createdBy) {
        return find("sessionId = ?1 AND createdBy = ?2", sessionId, createdBy).firstResultOptional();
    }

    /** 查询某 session 的所有评分(每个 createdBy 一条),用于 getScore 聚合。 */
    public List<SessionScoreEntity> findBySessionId(UUID sessionId) {
        return find("sessionId", sessionId).list();
    }
}
