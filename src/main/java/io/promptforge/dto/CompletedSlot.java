package io.promptforge.dto;

import java.util.UUID;

public record CompletedSlot(
        UUID slotId,
        String slotName,
        int orderIndex,
        String value
) {
}
