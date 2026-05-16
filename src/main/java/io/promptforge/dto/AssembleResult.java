package io.promptforge.dto;

public record AssembleResult(
        boolean success,
        String prompt,
        String message
) {
    public static AssembleResult success(String prompt) {
        return new AssembleResult(true, prompt, null);
    }

    public static AssembleResult failure(String message) {
        return new AssembleResult(false, null, message);
    }
}
