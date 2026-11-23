package com.datingapp.service;

import com.datingapp.dto.MatchResponse;
import com.datingapp.entity.Match;
import com.datingapp.entity.User;
import com.datingapp.exception.NotFoundException;
import com.datingapp.repository.MatchRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class MatchService {

    private final MatchRepository matchRepository;
    private final UserService userService;

    public MatchService(MatchRepository matchRepository, UserService userService) {
        this.matchRepository = matchRepository;
        this.userService = userService;
    }

    /** Idempotent: returns the existing match for the pair if there is one. */
    @Transactional
    public Match createMatch(User a, User b) {
        Match candidate = Match.between(a, b);
        return matchRepository
                .findByUser1IdAndUser2Id(candidate.getUser1().getId(), candidate.getUser2().getId())
                .orElseGet(() -> matchRepository.save(candidate));
    }

    public List<MatchResponse> getActiveMatches(Long userId) {
        User me = userService.requireUser(userId);
        return matchRepository.findActiveMatchesWithUsers(userId).stream()
                .map(m -> MatchResponse.of(m, me))
                .toList();
    }

    public MatchResponse getMatch(Long userId, Long matchId) {
        User me = userService.requireUser(userId);
        Match match = matchRepository.findByIdWithUsers(matchId)
                .filter(m -> m.involves(userId))
                .orElseThrow(MatchService::matchNotFound);
        return MatchResponse.of(match, me);
    }

    /** Idempotent. */
    @Transactional
    public void unmatch(Long userId, Long matchId) {
        requireParticipant(matchId, userId).setActive(false);
    }

    /**
     * Loads the match if {@code userId} participates in it. Non-participants get the same 404 as a
     * missing match so match ids can't be probed.
     */
    Match requireParticipant(Long matchId, Long userId) {
        return matchRepository.findById(matchId)
                .filter(m -> m.involves(userId))
                .orElseThrow(MatchService::matchNotFound);
    }

    private static NotFoundException matchNotFound() {
        return new NotFoundException("Match not found");
    }
}
