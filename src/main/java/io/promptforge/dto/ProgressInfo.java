package io.promptforge.dto;

public record ProgressInfo(
        boolean isCurrent,
        int completedCount,
        int totalCount,
        String currentSlotName
) {
}
