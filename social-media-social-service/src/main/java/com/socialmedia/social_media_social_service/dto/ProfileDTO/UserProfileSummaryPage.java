package com.socialmedia.social_media_social_service.dto.ProfileDTO;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileSummaryPage {

    private List<UserProfileSummary> content;

    private int page;

    private int size;

    private long totalElements;

    private int totalPages;

    private boolean first;

    private boolean last;

    private boolean empty;
}