package com.datingapp.controller;

import com.datingapp.dto.ChatMessageRequest;
import com.datingapp.dto.ChatMessageResponse;
import com.datingapp.dto.MatchResponse;
import com.datingapp.security.CurrentUserId;
import com.datingapp.service.ChatService;
import com.datingapp.service.MatchService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/matches")
public class MatchController {

    private final MatchService matchService;
    private final ChatService chatService;

    public MatchController(MatchService matchService, ChatService chatService) {
        this.matchService = matchService;
        this.chatService = chatService;
    }

    @GetMapping
    public List<MatchResponse> getMyMatches(@CurrentUserId Long userId) {
        return matchService.getActiveMatches(userId);
    }

    @GetMapping("/{matchId}")
    public MatchResponse getMatch(@CurrentUserId Long userId, @PathVariable Long matchId) {
        return matchService.getMatch(userId, matchId);
    }

    @PostMapping("/{matchId}/unmatch")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unmatch(@CurrentUserId Long userId, @PathVariable Long matchId) {
        matchService.unmatch(userId, matchId);
    }

    /** Chronological page of messages; pass the smallest id you have as {@code beforeId} for older ones. */
    @GetMapping("/{matchId}/messages")
    public List<ChatMessageResponse> getMessages(
            @CurrentUserId Long userId,
            @PathVariable Long matchId,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        return chatService.getMessages(userId, matchId, beforeId, size);
    }

    @PostMapping("/{matchId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public ChatMessageResponse sendMessage(@CurrentUserId Long userId,
                                           @PathVariable Long matchId,
                                           @Valid @RequestBody ChatMessageRequest request) {
        return chatService.sendMessage(matchId, userId, request.content());
    }

    @PostMapping("/{matchId}/messages/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAsRead(@CurrentUserId Long userId, @PathVariable Long matchId) {
        chatService.markMessagesAsRead(userId, matchId);
    }
}
