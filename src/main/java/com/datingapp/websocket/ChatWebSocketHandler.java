package com.datingapp.websocket;

import com.datingapp.exception.ApiException;
import com.datingapp.security.JwtHandshakeInterceptor;
import com.datingapp.service.ChatService;
import com.datingapp.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

/**
 * Real-time chat endpoint.
 *
 * <p>Connect: {@code ws://host/ws/chat?access_token=<JWT>} (or send {@code Authorization: Bearer}).
 *
 * <p>Send: {@code {"type":"CHAT","matchId":1,"content":"Hello!"}}
 *
 * <p>Receive:
 * <ul>
 *   <li>{@code {"type":"CHAT","messageId":1,"matchId":1,"senderId":2,"content":"Hello!","sentAt":"2026-01-01T12:00:00Z"}}
 *       — delivered to both participants, including the sender's own sessions as confirmation.</li>
 *   <li>{@code {"type":"MATCH","matchId":1,"matchedUserId":3,"matchedUserName":"Jane"}}</li>
 *   <li>{@code {"type":"ERROR","message":"..."}} — the last frame was rejected; the connection stays open.</li>
 * </ul>
 * Messages sent via the REST API are pushed the same way.
 */
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketHandler.class);

    private final WebSocketSessionRegistry registry;
    private final ChatService chatService;
    private final UserService userService;
    private final JsonMapper jsonMapper;

    public ChatWebSocketHandler(WebSocketSessionRegistry registry,
                                ChatService chatService,
                                UserService userService,
                                JsonMapper jsonMapper) {
        this.registry = registry;
        this.chatService = chatService;
        this.userService = userService;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException {
        Long userId = userId(session);
        if (userId == null) {
            // Should be unreachable: the handshake interceptor rejects unauthenticated upgrades.
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        registry.register(userId, session);
        userService.touchLastActive(userId);
        log.debug("User {} connected via WebSocket (session {})", userId, session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        Long senderId = userId(session);
        if (senderId == null) {
            return;
        }

        WebSocketPayloads.Inbound inbound;
        try {
            inbound = jsonMapper.readValue(message.getPayload(), WebSocketPayloads.Inbound.class);
        } catch (JacksonException e) {
            reject(senderId, session, "Malformed JSON");
            return;
        }

        String type = inbound.type() == null ? "CHAT" : inbound.type();
        if (!"CHAT".equals(type)) {
            reject(senderId, session, "Unsupported message type: " + type);
            return;
        }
        if (inbound.matchId() == null) {
            reject(senderId, session, "matchId is required");
            return;
        }

        try {
            // Delivery to both participants happens via ChatMessageSentEvent after commit.
            chatService.sendMessage(inbound.matchId(), senderId, inbound.content());
        } catch (ApiException e) {
            reject(senderId, session, e.getMessage());
        } catch (RuntimeException e) {
            log.error("Failed to process WebSocket message from user {}", senderId, e);
            reject(senderId, session, "Internal error");
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = userId(session);
        if (userId != null) {
            registry.unregister(userId, session);
            log.debug("User {} disconnected (session {}, {})", userId, session.getId(), status);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("WebSocket transport error for session {}: {}", session.getId(), exception.getMessage());
    }

    private void reject(Long userId, WebSocketSession session, String reason) {
        registry.sendToSession(userId, session, new WebSocketPayloads.Error(reason));
    }

    private static Long userId(WebSocketSession session) {
        return session.getAttributes().get(JwtHandshakeInterceptor.USER_ID_ATTRIBUTE) instanceof Long id ? id : null;
    }
}
