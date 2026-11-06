package com.datingapp.websocket;

import com.datingapp.event.ChatMessageSentEvent;
import com.datingapp.event.MatchCreatedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Pushes domain events to connected clients. Runs only after the originating transaction commits,
 * so clients never see a match or message that was rolled back.
 */
@Component
public class RealtimeNotificationListener {

    private final WebSocketSessionRegistry registry;

    public RealtimeNotificationListener(WebSocketSessionRegistry registry) {
        this.registry = registry;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMatchCreated(MatchCreatedEvent e) {
        registry.sendToUser(e.user1Id(), new WebSocketPayloads.MatchNotification(e.matchId(), e.user2Id(), e.user2Name()));
        registry.sendToUser(e.user2Id(), new WebSocketPayloads.MatchNotification(e.matchId(), e.user1Id(), e.user1Name()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onChatMessageSent(ChatMessageSentEvent e) {
        WebSocketPayloads.Chat payload = new WebSocketPayloads.Chat(
                e.messageId(), e.matchId(), e.senderId(), e.content(), e.sentAt());
        registry.sendToUser(e.recipientId(), payload);
        registry.sendToUser(e.senderId(), payload);
    }
}
