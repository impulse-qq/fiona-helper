package io.promptforge.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "story_character")
public class CharacterEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    public UUID id;

    @Column(name = "name", nullable = false, length = 128)
    public String name;

    @Column(name = "base_design", length = 512)
    public String baseDesign;

    @Column(name = "personality", length = 512)
    public String personality;

    @Column(name = "avatar_path", length = 512)
    public String avatarPath;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    public Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    public Instant updatedAt;

    public CharacterEntity() {
    }

    public CharacterEntity(String name, String baseDesign, String personality) {
        this.name = name;
        this.baseDesign = baseDesign;
        this.personality = personality;
    }
}
