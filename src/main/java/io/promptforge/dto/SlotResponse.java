package io.promptforge.dto;

import java.util.List;

public record SlotResponse(
        SlotInfo slot,
        ProgressInfo progress,
        List<CompletedSlot> completedSlots,
        NextStep nextStep
) {
}
