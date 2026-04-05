package com.socialmedia.social_media_chat_service.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import com.socialmedia.social_media_chat_service.document.enums.MemberRole;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "conversation_members")
@CompoundIndexes({
    @CompoundIndex(name = "idx_conv_user_unique", def = "{'conversationId': 1, 'userId': 1}", unique = true),
    @CompoundIndex(name = "idx_user_hidden_read", def = "{'userId': 1, 'hidden': 1, 'lastReadAt': -1}")
})
public class ConversationMemberDocument {

    @Id
    private String id;

    private String conversationId;
    private String userId;

    @Builder.Default
    private MemberRole role = MemberRole.MEMBER;

    private Instant joinedAt;

    private String lastReadMessageId;
    private Instant lastReadAt;

    private String lastDeliveredMessageId;

    @Builder.Default
    private int unreadCount = 0;

    @Builder.Default
    private boolean muted = false;

    @Builder.Default
    private boolean pinned = false;

    @Builder.Default
    private boolean hidden = false;

    @Builder.Default
    private boolean active = true;
}
