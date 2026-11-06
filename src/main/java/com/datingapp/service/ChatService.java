package com.datingapp.service;

import com.datingapp.dto.ChatMessageResponse;
import com.datingapp.entity.ChatMessage;
import com.datingapp.entity.Match;
import com.datingapp.event.ChatMessageSentEvent;
import com.datingapp.exception.BadRequestException;
import com.datingapp.exception.ConflictException;
import com.datingapp.repository.ChatMessageRepository;
import com.datingapp.repository.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class ChatService {

    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final MatchService matchService;
    private final ApplicationEventPublisher events;

    public ChatService(ChatMessageRepository chatMessageRepository,
                       UserRepository userRepository,
                       MatchService matchService,
                       ApplicationEventPublisher events) {
        this.chatMessageRepository = chatMessageRepository;
        this.userRepository = userRepository;
        this.matchService = matchService;
        this.events = events;
    }

    /**
     * Persists a message and publishes an event so both participants are notified in real time,
     * regardless of whether it arrived over REST or WebSocket.
     */
    @Transactional
    public ChatMessageResponse sendMessage(Long matchId, Long senderId, String content) {
        if (content == null || content.isBlank()) {
            throw new BadRequestException("Message content must not be blank");
        }
        if (content.length() > ChatMessage.MAX_CONTENT_LENGTH) {
            throw new BadRequestException("Message content must be at most "
                    + ChatMessage.MAX_CONTENT_LENGTH + " characters");
        }

        Match match = matchService.requireParticipant(matchId, senderId);
        if (!match.isActive()) {
            throw new ConflictException("Cannot send messages to an inactive match");
        }

        ChatMessage saved = chatMessageRepository.save(
                new ChatMessage(match, userRepository.getReferenceById(senderId), content));

        Long recipientId = match.otherParticipant(senderId).getId();
        events.publishEvent(new ChatMessageSentEvent(saved.getId(), matchId, senderId, recipientId,
                saved.getContent(), saved.getSentAt()));
        return ChatMessageResponse.from(saved);
    }

    /**
     * Returns up to {@code size} messages in chronological order. Pass the smallest id from the
     * previous page as {@code beforeId} to load older messages.
     */
    public List<ChatMessageResponse> getMessages(Long userId, Long matchId, Long beforeId, int size) {
        matchService.requireParticipant(matchId, userId);
        PageRequest page = PageRequest.of(0, size);
        List<ChatMessage> newestFirst = beforeId == null
                ? chatMessageRepository.findByMatchIdOrderByIdDesc(matchId, page)
                : chatMessageRepository.findByMatchIdAndIdLessThanOrderByIdDesc(matchId, beforeId, page);

        List<ChatMessageResponse> result = new ArrayList<>(newestFirst.size());
        newestFirst.forEach(m -> result.add(ChatMessageResponse.from(m)));
        Collections.reverse(result);
        return result;
    }

    @Transactional
    public void markMessagesAsRead(Long userId, Long matchId) {
        matchService.requireParticipant(matchId, userId);
        chatMessageRepository.markReadForRecipient(matchId, userId);
    }
}
