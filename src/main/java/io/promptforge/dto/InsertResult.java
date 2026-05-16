package io.promptforge.dto;

import io.promptforge.entity.SessionStatus;

public record InsertResult(
        boolean success,
        String message,
        SessionStatus sessionStatus,
        NextStep nextStep
) {
}
