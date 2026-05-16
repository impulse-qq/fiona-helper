package io.promptforge.dto;

import java.util.UUID;

public record ScoreSubmitResult(
    boolean success,
    String message,
    UUID scoreId,
    boolean isUpdate
) {}
