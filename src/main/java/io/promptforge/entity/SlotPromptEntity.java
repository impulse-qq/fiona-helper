package io.promptforge.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * 可复用的 Slot Prompt 内容池。
 * - session_id=NULL: 预设/手维护内容(M1 的 default_value 迁移而来)
 * - session_id 非空: 某次 session 通过 insert_slot_value 沉淀的实际填充值
 * - character_id 预留 M3 list_slot_prompts 工具按角色过滤
 */
@Entity
@Table(name = "slot_prompt")
public class SlotPromptEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    public UUID id;

    @Column(name = "slot_id", nullable = false)
    public UUID slotId;

    @Column(name = "character_id")
    public UUID characterId;

    @Column(name = "session_id")
    public UUID sessionId;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    public String content;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    public Instant createdAt;

    @Column(name = "created_by", length = 64)
    public String createdBy;

    public SlotPromptEntity() {
    }

    public SlotPromptEntity(UUID slotId, UUID characterId, UUID sessionId, String content, String createdBy) {
        this.slotId = slotId;
        this.characterId = characterId;
        this.sessionId = sessionId;
        this.content = content;
        this.createdBy = createdBy;
    }
}
