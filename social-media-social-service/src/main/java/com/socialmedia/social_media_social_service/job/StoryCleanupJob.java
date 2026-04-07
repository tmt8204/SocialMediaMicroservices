package com.socialmedia.social_media_social_service.job;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.socialmedia.social_media_social_service.service.StoryService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@RequiredArgsConstructor
public class StoryCleanupJob {

    private final StoryService storyService;

    @Value("${app.story.cleanup.batch-size:100}")
    private int batchSize;

    @Value("${app.story.cleanup.grace-period-ms:3600000}")
    private long gracePeriodMs;

    @Scheduled(fixedDelayString = "${app.story.cleanup.fixed-delay-ms:900000}")
    public void cleanupExpiredStories() {
        int deletedCount = storyService.cleanupStories(batchSize, gracePeriodMs);
        if (deletedCount > 0) {
            log.info("Story cleanup removed {} stories.", deletedCount);
        }
    }
}