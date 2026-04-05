package com.socialmedia.social_media_chat_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessagePageResponse {
    private List<MessageResponse> messages;
    private long nextCursor;
    private boolean hasMore;
    private int totalCount;
}
