package io.promptforge.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "session_image")
public class SessionImageEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    public UUID id;

    @Column(name = "session_id", nullable = false)
    public UUID sessionId;

    @Column(name = "image_path", nullable = false, length = 512)
    public String imagePath;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    public Instant createdAt;

    public SessionImageEntity() {}

    public SessionImageEntity(UUID sessionId, String imagePath) {
        this.sessionId = sessionId;
        this.imagePath = imagePath;
    }

    public String getImageUrl() {
        String filename = Path.of(imagePath).getFileName().toString();
        return "/api/session-images/" + sessionId + "/" + filename;
    }
}
