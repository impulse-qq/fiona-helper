package io.promptforge.dto;

import java.util.UUID;

public record CharacterSettingResponse(
        UUID characterId,
        String name,
        String baseDesign,
        String personality
) {
}
