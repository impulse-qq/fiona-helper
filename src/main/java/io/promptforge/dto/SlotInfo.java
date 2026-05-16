package io.promptforge.dto;

import io.promptforge.entity.ConstraintType;

import java.util.UUID;

public record SlotInfo(
        UUID id,
        String name,
        int orderIndex,
        ConstraintType constraintType,
        String description,
        Integer wordLimit
) {
}
