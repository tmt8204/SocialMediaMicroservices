package com.socialmedia.social_media_chat_service.repository;

import com.socialmedia.social_media_chat_service.document.ConversationMemberDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationMemberRepository extends MongoRepository<ConversationMemberDocument, String> {

    Optional<ConversationMemberDocument> findByConversationIdAndUserId(String conversationId, String userId);

    List<ConversationMemberDocument> findByConversationId(String conversationId);

    List<ConversationMemberDocument> findByUserIdAndHiddenFalseAndActiveTrue(String userId);

    @Query(value = "{ 'userId': ?0, 'hidden': false, 'lastReadAt': { $exists: true } }", sort = "{ 'lastReadAt': -1 }")
    List<ConversationMemberDocument> findByUserIdSortedByLastRead(String userId);

    List<ConversationMemberDocument> findByConversationIdAndActiveTrue(String conversationId);

    List<ConversationMemberDocument> findByConversationIdAndUserIdIn(String conversationId, List<String> userIds);

    int countByConversationIdAndUnreadCountGreaterThan(String conversationId, int count);
}
