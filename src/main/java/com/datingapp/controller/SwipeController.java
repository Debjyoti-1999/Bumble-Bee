package com.datingapp.controller;

import com.datingapp.dto.SwipeRequest;
import com.datingapp.dto.SwipeResponse;
import com.datingapp.security.CurrentUserId;
import com.datingapp.service.SwipeService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/swipes")
public class SwipeController {

    private final SwipeService swipeService;

    public SwipeController(SwipeService swipeService) {
        this.swipeService = swipeService;
    }

    @PostMapping
    public SwipeResponse swipe(@CurrentUserId Long userId, @Valid @RequestBody SwipeRequest request) {
        return swipeService.processSwipe(userId, request.swipedId(), request.liked());
    }
}
