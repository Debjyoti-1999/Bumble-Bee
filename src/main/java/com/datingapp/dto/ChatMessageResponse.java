package com.datingapp.dto;

import com.datingapp.entity.ChatMessage;

import java.time.Instant;

public record ChatMessageResponse(
        Long id,
        Long matchId,
        Long senderId,
        String content,
        Instant sentAt,
        boolean read
) {
    public static ChatMessageResponse from(ChatMessage m) {
        // getId() on a lazy proxy does not trigger a load.
        return new ChatMessageResponse(m.getId(), m.getMatch().getId(), m.getSender().getId(),
                m.getContent(), m.getSentAt(), m.isRead());
    }
}
