package io.promptforge.dto;

import java.util.List;
import java.util.UUID;

public record ScoreResponse(
    UUID sessionId,
    List<ScoreItem> scores,
    Double avgScore,
    int scoreCount
) {}
