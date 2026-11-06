package com.datingapp.controller;

import com.datingapp.dto.PublicProfileResponse;
import com.datingapp.dto.UpdateProfileRequest;
import com.datingapp.dto.UserProfileResponse;
import com.datingapp.security.CurrentUserId;
import com.datingapp.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public UserProfileResponse getMe(@CurrentUserId Long userId) {
        return userService.getOwnProfile(userId);
    }

    @PutMapping("/me")
    public UserProfileResponse updateMe(@CurrentUserId Long userId,
                                        @Valid @RequestBody UpdateProfileRequest request) {
        return userService.updateProfile(userId, request);
    }

    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMe(@CurrentUserId Long userId) {
        userService.deleteAccount(userId);
    }

    @GetMapping("/me/potential-matches")
    public List<PublicProfileResponse> getPotentialMatches(
            @CurrentUserId Long userId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return userService.getPotentialMatches(userId, page, size);
    }

    @GetMapping("/{id}")
    public PublicProfileResponse getUser(@CurrentUserId Long viewerId, @PathVariable Long id) {
        return userService.getPublicProfile(viewerId, id);
    }
}
