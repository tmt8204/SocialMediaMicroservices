package com.socialmedia.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StatsResponse {
    private long totalUsers;
    private long totalPosts;
    private long totalComments;
    private long totalCommunities;
    private long totalBannedUsers;
}
