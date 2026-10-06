package com.mainuddin.codereview.controller;

import com.mainuddin.codereview.dto.UiCapabilityDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.mainuddin.codereview.dto.UiStatusDTO;
import com.mainuddin.codereview.service.UiStatusService;
import org.springframework.security.core.context.SecurityContextHolder;

@RestController
@RequestMapping("/api/settings")
public class UiCapabilityController {
    private final boolean enabled;
    private final String key;
    private final String model;
    private final UiStatusService statusService;

    public UiCapabilityController(@Value("${app.llm.enabled:false}") boolean enabled,
                                  @Value("${app.llm.api-key:}") String key,
                                  @Value("${app.llm.model:gpt-5-mini}") String model,
                                  UiStatusService statusService) {
        this.enabled = enabled; this.key = key; this.model = model; this.statusService = statusService;
    }

    @GetMapping("/capabilities")
    public UiCapabilityDTO capabilities() {
        return new UiCapabilityDTO(enabled, !key.isBlank(), model);
    }

    @GetMapping("/status")
    public UiStatusDTO status() {
        return statusService.status(SecurityContextHolder.getContext().getAuthentication().getName());
    }
}
