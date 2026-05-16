package io.promptforge.dto;

import io.promptforge.entity.SessionStatus;

import java.util.UUID;

public record SessionResponse(
        UUID sessionId,
        SessionStatus status,
        SlotInfo firstSlot,
        NextStep nextStep,
        String note
) {
}
