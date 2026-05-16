package io.promptforge.dto;

import java.util.UUID;

/** Pipeline 列表条目摘要 */
public record PipelineSummary(
        UUID id,
        String name,
        String description,
        String worldSetting
) {
}
