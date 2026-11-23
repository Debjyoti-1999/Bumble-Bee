package com.datingapp.event;

import java.time.Instant;

/** Published inside the send-message transaction; listeners act after commit. */
public record ChatMessageSentEvent(
        Long messageId,
        Long matchId,
        Long senderId,
        Long recipientId,
        String content,
        Instant sentAt
) {
}
