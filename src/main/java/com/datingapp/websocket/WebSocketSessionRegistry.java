package com.datingapp.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks open sessions per user on this instance.
 *
 * <p>Note: this is in-memory, so pushes only reach users connected to the same instance. To run
 * more than one instance, fan events out through a shared broker (e.g. Redis pub/sub or a STOMP
 * broker relay) and have each instance deliver to its local sessions via this registry.
 */
@Component
public class WebSocketSessionRegistry {

    private static final Logger log = LoggerFactory.getLogger(WebSocketSessionRegistry.class);
    private static final int SEND_TIME_LIMIT_MS = 10_000;
    private static final int SEND_BUFFER_LIMIT_BYTES = 512 * 1024;

    /** userId -> (sessionId -> thread-safe session). */
    private final Map<Long, Map<String, WebSocketSession>> sessionsByUser = new ConcurrentHashMap<>();
    private final JsonMapper jsonMapper;

    public WebSocketSessionRegistry(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public void register(Long userId, WebSocketSession session) {
        // Raw sessions don't allow concurrent sends; the decorator buffers and serializes them.
        WebSocketSession safe = new ConcurrentWebSocketSessionDecorator(
                session, SEND_TIME_LIMIT_MS, SEND_BUFFER_LIMIT_BYTES);
        sessionsByUser.compute(userId, (id, sessions) -> {
            Map<String, WebSocketSession> map = sessions != null ? sessions : new ConcurrentHashMap<>();
            map.put(session.getId(), safe);
            return map;
        });
    }

    public void unregister(Long userId, WebSocketSession session) {
        sessionsByUser.computeIfPresent(userId, (id, sessions) -> {
            sessions.remove(session.getId());
            return sessions.isEmpty() ? null : sessions;
        });
    }

    /** Sends to every open session of the user on this instance. */
    public void sendToUser(Long userId, Object payload) {
        Map<String, WebSocketSession> sessions = sessionsByUser.get(userId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        TextMessage message = new TextMessage(jsonMapper.writeValueAsString(payload));
        sessions.values().forEach(s -> send(s, message));
    }

    /** Sends to one specific session (e.g. an error reply), using its thread-safe wrapper. */
    public void sendToSession(Long userId, WebSocketSession session, Object payload) {
        Map<String, WebSocketSession> sessions = sessionsByUser.get(userId);
        WebSocketSession safe = sessions != null ? sessions.get(session.getId()) : null;
        if (safe != null) {
            send(safe, new TextMessage(jsonMapper.writeValueAsString(payload)));
        }
    }

    private static void send(WebSocketSession session, TextMessage message) {
        if (!session.isOpen()) {
            return;
        }
        try {
            session.sendMessage(message);
        } catch (IOException | RuntimeException e) {
            // Includes SessionLimitExceededException for slow consumers; the decorator closes those.
            log.warn("Failed to send WebSocket message to session {}: {}", session.getId(), e.getMessage());
        }
    }
}
