package io.promptforge.dto;

import java.util.List;
import java.util.UUID;

/** Pipeline 详情含所有 Slot */
public record PipelineDetail(
        UUID id,
        String name,
        String description,
        String worldSetting,
        List<SlotInfo> slots
) {
}
