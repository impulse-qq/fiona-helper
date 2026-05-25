package io.promptforge.dto;

import io.promptforge.entity.SessionStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SessionDetail(
        UUID id,
        UUID pipelineId,
        String pipelineName,
        String worldSetting,
        UUID characterId,
        String characterName,
        SessionStatus status,
        List<SlotPromptItem> slots,
        List<ImageItem> images,
        Instant createdAt
) {}
