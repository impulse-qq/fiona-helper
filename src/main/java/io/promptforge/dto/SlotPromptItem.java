package io.promptforge.dto;

import java.util.UUID;

public record SlotPromptItem(
        UUID slotId,
        String slotName,
        int orderIndex,
        String content
) {}
