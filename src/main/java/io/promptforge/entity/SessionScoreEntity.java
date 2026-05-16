package io.promptforge.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Session 粒度的评分反馈(M3a 引入)。
 * - 关联 {@link AssembleSessionEntity},仅在 status = COMPLETED 时允许评分。
 * - UNIQUE(session_id, created_by):同一 createdBy 重提交 → 覆盖。
 * - overall_score 范围 1-5,DB 层 CHECK 约束。
 */
@Entity
@Table(
    name = "session_score",
    uniqueConstraints = @UniqueConstraint(name = "uk_session_score_session_creator",
                                          columnNames = {"session_id", "created_by"})
)
public class SessionScoreEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    public UUID id;

    @Column(name = "session_id", nullable = false)
    public UUID sessionId;

    @Column(name = "overall_score", nullable = false)
    public int overallScore;

    @Column(name = "comment", columnDefinition = "TEXT")
    public String comment;

    @Column(name = "created_by", nullable = false, length = 64)
    public String createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    public Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    public Instant updatedAt;

    public SessionScoreEntity() {
    }

    public SessionScoreEntity(UUID sessionId, int overallScore, String comment, String createdBy) {
        this.sessionId = sessionId;
        this.overallScore = overallScore;
        this.comment = comment;
        this.createdBy = createdBy;
    }
}
