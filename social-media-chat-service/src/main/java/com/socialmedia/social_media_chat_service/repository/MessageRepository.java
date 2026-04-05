package com.socialmedia.social_media_chat_service.repository;

import com.socialmedia.social_media_chat_service.document.MessageDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface MessageRepository extends MongoRepository<MessageDocument, String> {

    Optional<MessageDocument> findByIdAndConversationId(String messageId, String conversationId);

    @Query(value = "{ 'conversationId': ?0, 'seqNo': { $lt: ?1 } }", sort = "{ 'seqNo': -1 }")
    Page<MessageDocument> findByConversationIdPaginated(String conversationId, long seqNo, Pageable pageable);

    @Query(value = "{ 'conversationId': ?0 }", sort = "{ 'seqNo': -1 }")
    Page<MessageDocument> findByConversationIdDesc(String conversationId, Pageable pageable);

    @Query(value = "{ 'conversationId': ?0 }", sort = "{ 'createdAt': -1 }")
    Page<MessageDocument> findByConversationIdSortedByCreatedAt(String conversationId, Pageable pageable);

    List<MessageDocument> findBySenderId(String senderId);

    long countByConversationId(String conversationId);

    Optional<MessageDocument> findFirstByConversationIdOrderBySeqNoDesc(String conversationId);

    List<MessageDocument> findByConversationIdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
            String conversationId, Instant fromTime
    );
}
