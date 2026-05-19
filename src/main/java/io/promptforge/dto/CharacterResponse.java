package io.promptforge.dto;

import java.util.UUID;

public record CharacterResponse(
        UUID id,
        String name,
        String baseDesign,
        String personality,
        String avatarUrl
) {
}
