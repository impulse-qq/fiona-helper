package io.promptforge.dto;

import java.util.UUID;

public record NextStep(
        UUID slotId,
        String slotName,
        String hint
) {
}
