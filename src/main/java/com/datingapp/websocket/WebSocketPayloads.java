package com.datingapp.websocket;

import java.time.Instant;

/** Wire formats for the /ws/chat endpoint. */
public final class WebSocketPayloads {

    private WebSocketPayloads() {
    }

    /** Client -> server. {@code type} defaults to CHAT when omitted. */
    public record Inbound(String type, Long matchId, String content) {
    }

    /** Server -> client: a chat message (also echoed to the sender's sessions as confirmation). */
    public record Chat(String type, Long messageId, Long matchId, Long senderId, String content, Instant sentAt) {
        public Chat(Long messageId, Long matchId, Long senderId, String content, Instant sentAt) {
            this("CHAT", messageId, matchId, senderId, content, sentAt);
        }
    }

    /** Server -> client: a new mutual match. */
    public record MatchNotification(String type, Long matchId, Long matchedUserId, String matchedUserName) {
        public MatchNotification(Long matchId, Long matchedUserId, String matchedUserName) {
            this("MATCH", matchId, matchedUserId, matchedUserName);
        }
    }

    /** Server -> client: the last inbound frame was rejected. The connection stays open. */
    public record Error(String type, String message) {
        public Error(String message) {
            this("ERROR", message);
        }
    }
}
