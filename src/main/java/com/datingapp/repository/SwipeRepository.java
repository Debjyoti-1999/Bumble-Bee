package com.datingapp.repository;

import com.datingapp.entity.Swipe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SwipeRepository extends JpaRepository<Swipe, Long> {

    boolean existsBySwiperIdAndSwipedId(Long swiperId, Long swipedId);

    boolean existsBySwiperIdAndSwipedIdAndLikedTrue(Long swiperId, Long swipedId);

    @Modifying
    @Query("DELETE FROM Swipe s WHERE s.swiper.id = :userId OR s.swiped.id = :userId")
    int deleteAllInvolvingUser(@Param("userId") Long userId);
}
