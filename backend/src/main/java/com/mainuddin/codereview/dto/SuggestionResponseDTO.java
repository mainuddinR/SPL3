package com.mainuddin.codereview.dto;

import java.time.LocalDateTime;

public record SuggestionResponseDTO(Long findingId, String status, String suggestion,
                                    LocalDateTime generatedAt, String providerModel,
                                    String promptVersion, String reasonCode, String reason,
                                    Integer retryAfterSeconds) {
    public static SuggestionResponseDTO unavailable(Long id, String reasonCode, String reason) {
        return unavailable(id, reasonCode, reason, null);
    }
    public static SuggestionResponseDTO unavailable(Long id, String reasonCode, String reason, Integer retryAfterSeconds) {
        return new SuggestionResponseDTO(id, "UNAVAILABLE", null, null, null, null, reasonCode, reason, retryAfterSeconds);
    }
}
