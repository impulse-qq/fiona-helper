package io.promptforge.dto;

import java.time.Instant;
import java.util.UUID;

public record ScoreItem(
    UUID id,
    int overallScore,
    String comment,
    String createdBy,
    Instant createdAt,
    Instant updatedAt
) {}
