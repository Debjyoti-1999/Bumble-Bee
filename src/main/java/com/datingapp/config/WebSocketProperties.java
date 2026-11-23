package com.datingapp.config;

import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/** @param allowedOrigins browser origins allowed to open a WebSocket, e.g. https://app.example.com */
@Validated
@ConfigurationProperties("app.websocket")
public record WebSocketProperties(@NotEmpty List<String> allowedOrigins) {
}
