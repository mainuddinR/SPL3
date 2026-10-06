package com.mainuddin.codereview.dto;

public record UiStatusDTO(String githubId, String username, String avatarUrl,
        long connectedRepositoryCount, boolean githubSessionConnected,
        boolean mlServiceAvailable, boolean databaseConnected, UiCapabilityDTO ai) {}
