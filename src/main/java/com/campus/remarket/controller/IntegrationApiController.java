package com.campus.remarket.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class IntegrationApiController {
    private final String mapsKey;

    public IntegrationApiController(@Value("${integrations.google-maps-key:}") String mapsKey) {
        this.mapsKey = mapsKey;
    }

    @GetMapping("/api/integrations/config")
    public Map<String, String> config() {
        return Map.of("mapsKey", mapsKey);
    }
}
