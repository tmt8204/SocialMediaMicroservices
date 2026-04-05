package com.socialmedia.social_media_chat_service.repository;

import com.socialmedia.social_media_chat_service.document.ConversationDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationRepository extends MongoRepository<ConversationDocument, String> {

    Optional<ConversationDocument> findByDirectKey(String directKey);

    List<ConversationDocument> findByParticipantIdsContainingAndActiveTrue(String userId);

    @Query(value = "{ 'participantIds': ?0, 'active': true }", sort = "{ 'lastMessageAt': -1 }")
    List<ConversationDocument> findByUserIdSortedByLastMessage(String userId);

    List<ConversationDocument> findByParticipantIdsContainingAndActiveIsFalse(String userId);

    @Query(value = "{ 'participantIds': { $all: ?0 }, 'type': 'DIRECT' }")
    Optional<ConversationDocument> findDirectConversationByParticipants(List<String> participantIds);
}
