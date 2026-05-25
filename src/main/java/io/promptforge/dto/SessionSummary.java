package io.promptforge.dto;

import io.promptforge.entity.SessionStatus;

import java.time.Instant;
import java.util.UUID;

public record SessionSummary(
        UUID id,
        UUID pipelineId,
        UUID characterId,
        String characterName,
        SessionStatus status,
        Instant createdAt,
        int imageCount
) {}
