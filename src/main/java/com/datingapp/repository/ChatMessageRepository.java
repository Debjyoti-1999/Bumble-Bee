package com.datingapp.repository;

import com.datingapp.entity.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /** Newest page of a conversation (ids are monotonically increasing). */
    List<ChatMessage> findByMatchIdOrderByIdDesc(Long matchId, Pageable pageable);

    /** Older page of a conversation, for cursor-based "load more". */
    List<ChatMessage> findByMatchIdAndIdLessThanOrderByIdDesc(Long matchId, Long beforeId, Pageable pageable);

    @Modifying
    @Query("""
            UPDATE ChatMessage m SET m.read = true
            WHERE m.match.id = :matchId AND m.sender.id <> :userId AND m.read = false
            """)
    int markReadForRecipient(@Param("matchId") Long matchId, @Param("userId") Long userId);

    @Modifying
    @Query("""
            DELETE FROM ChatMessage m WHERE m.match.id IN
              (SELECT x.id FROM Match x WHERE x.user1.id = :userId OR x.user2.id = :userId)
            """)
    int deleteAllInMatchesOfUser(@Param("userId") Long userId);
}
