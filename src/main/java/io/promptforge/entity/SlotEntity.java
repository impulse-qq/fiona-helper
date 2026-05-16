package io.promptforge.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pipeline_slot")
public class SlotEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pipeline_id", nullable = false)
    public PipelineEntity pipeline;

    @Column(name = "name", nullable = false, length = 64)
    public String name;

    @Column(name = "order_index", nullable = false)
    public int orderIndex;

    @Enumerated(EnumType.STRING)
    @Column(name = "constraint_type", nullable = false, length = 16)
    public ConstraintType constraintType;

    @Column(name = "default_value")
    public String defaultValue;

    /** Slot 描述/提示，帮助 Agent 理解该填什么，如 "请为角色生成详细的外貌描述" */
    @Column(name = "description", length = 512)
    public String description;

    /** 字数限制，null 表示无限制 */
    @Column(name = "word_limit")
    public Integer wordLimit;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    public Instant createdAt;

    public SlotEntity() {
    }

    public SlotEntity(String name, int orderIndex, ConstraintType constraintType, String defaultValue) {
        this.name = name;
        this.orderIndex = orderIndex;
        this.constraintType = constraintType;
        this.defaultValue = defaultValue;
    }
}
