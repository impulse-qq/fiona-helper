package io.promptforge.dto;

import java.util.UUID;

public record ImageItem(
        UUID id,
        String imageUrl
) {}
