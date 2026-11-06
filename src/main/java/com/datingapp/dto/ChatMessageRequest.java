package com.datingapp.dto;

import com.datingapp.entity.ChatMessage;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The sender is always the authenticated user. */
public record ChatMessageRequest(
        @NotBlank @Size(max = ChatMessage.MAX_CONTENT_LENGTH) String content
) {
}
