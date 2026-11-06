package com.datingapp;

import com.datingapp.dto.SwipeResponse;
import com.datingapp.repository.MatchRepository;
import com.datingapp.service.SwipeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

/** Regression test for the simultaneous mutual-like race (lost or duplicate matches). */
class ConcurrentSwipeTest extends ApiTestSupport {

    @Autowired
    private SwipeService swipeService;

    @Autowired
    private MatchRepository matchRepository;

    @Test
    void simultaneousMutualLikesProduceExactlyOneMatch() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int i = 0; i < 20; i++) {
                TestUser a = register("RaceA" + i);
                TestUser b = register("RaceB" + i);
                CyclicBarrier start = new CyclicBarrier(2);

                Callable<SwipeResponse> aLikesB = () -> {
                    start.await();
                    return swipeService.processSwipe(a.id(), b.id(), true);
                };
                Callable<SwipeResponse> bLikesA = () -> {
                    start.await();
                    return swipeService.processSwipe(b.id(), a.id(), true);
                };

                List<Future<SwipeResponse>> results = pool.invokeAll(List.of(aLikesB, bLikesA), 30, TimeUnit.SECONDS);
                long matchedCount = 0;
                for (Future<SwipeResponse> f : results) {
                    if (f.get().matched()) matchedCount++;
                }

                assertThat(matchedCount).as("iteration %d", i).isEqualTo(1);
                assertThat(matchRepository.findByUser1IdAndUser2Id(Math.min(a.id(), b.id()), Math.max(a.id(), b.id())))
                        .as("iteration %d", i).isPresent();
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
