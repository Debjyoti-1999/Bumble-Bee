package com.datingapp.service;

import com.datingapp.dto.SwipeResponse;
import com.datingapp.entity.Match;
import com.datingapp.entity.Swipe;
import com.datingapp.entity.User;
import com.datingapp.event.MatchCreatedEvent;
import com.datingapp.exception.BadRequestException;
import com.datingapp.exception.ConflictException;
import com.datingapp.exception.NotFoundException;
import com.datingapp.repository.SwipeRepository;
import com.datingapp.repository.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class SwipeService {

    private final SwipeRepository swipeRepository;
    private final UserRepository userRepository;
    private final MatchService matchService;
    private final ApplicationEventPublisher events;

    public SwipeService(SwipeRepository swipeRepository,
                        UserRepository userRepository,
                        MatchService matchService,
                        ApplicationEventPublisher events) {
        this.swipeRepository = swipeRepository;
        this.userRepository = userRepository;
        this.matchService = matchService;
        this.events = events;
    }

    /**
     * Records a swipe and creates a match on mutual like.
     *
     * <p>Two users liking each other at the same moment could otherwise both miss the other's
     * uncommitted swipe (no match) or both create one (duplicate). Locking the lower-id user's
     * row serializes all swipes within the pair, so the second transaction always sees the first.
     */
    @Transactional
    public SwipeResponse processSwipe(Long swiperId, Long swipedId, boolean liked) {
        if (swiperId.equals(swipedId)) {
            throw new BadRequestException("Cannot swipe on yourself");
        }

        long lowId = Math.min(swiperId, swipedId);
        long highId = Math.max(swiperId, swipedId);
        User low = userRepository.findByIdForUpdate(lowId).orElseThrow(SwipeService::userNotFound);
        User high = userRepository.findById(highId).orElseThrow(SwipeService::userNotFound);
        User swiper = swiperId == lowId ? low : high;
        User swiped = swiperId == lowId ? high : low;

        if (swipeRepository.existsBySwiperIdAndSwipedId(swiperId, swipedId)) {
            throw new ConflictException("Already swiped on this user");
        }

        swipeRepository.save(new Swipe(swiper, swiped, liked));
        swiper.setLastActive(Instant.now());

        if (liked && swipeRepository.existsBySwiperIdAndSwipedIdAndLikedTrue(swipedId, swiperId)) {
            Match match = matchService.createMatch(swiper, swiped);
            events.publishEvent(new MatchCreatedEvent(match.getId(),
                    swiper.getId(), swiper.getName(), swiped.getId(), swiped.getName()));
            return SwipeResponse.matched(match.getId());
        }
        return SwipeResponse.noMatch();
    }

    private static NotFoundException userNotFound() {
        return new NotFoundException("User not found");
    }
}
