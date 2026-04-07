package com.socialmedia.social_media_social_service.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.socialmedia.social_media_social_service.dto.ReactionDTO.ReactionResponse;
import com.socialmedia.social_media_social_service.entities.PostEntity;
import com.socialmedia.social_media_social_service.entities.ReactionsEntity;
import com.socialmedia.social_media_social_service.exceptions.ResourceNotFoundException;
import com.socialmedia.social_media_social_service.repositories.PostRepository;
import com.socialmedia.social_media_social_service.repositories.ReactionsRepository;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@Transactional
@AllArgsConstructor
public class ReactionService {

    private static final String DEFAULT_REACTION_TYPE = "LIKE";

    private final PostRepository postRepository;
    private final ReactionsRepository reactionsRepository;
    private final SocialNotificationEventProducer notificationEventProducer;

    public ReactionResponse reactToPost(String userId, Long postId, String type) {
        log.debug("reactToPost: userId={} postId={} type={}", userId, postId, type);
        PostEntity post = getActivePost(postId);
        String resolvedType = resolveType(type);

        java.util.Optional<ReactionsEntity> existing = reactionsRepository.findByUserIdAndPostId(userId, postId);
        if (existing.isEmpty()) {
            ReactionsEntity reaction = new ReactionsEntity();
            reaction.setUserId(userId);
            reaction.setPost(post);
            reaction.setType(resolvedType);
            reactionsRepository.save(reaction);

            post.setReactionsCount(post.getReactionsCount() + 1);
            postRepository.save(post);
            log.debug("reactToPost: new reaction saved, postId={} type={} totalReacts={}", postId, resolvedType, post.getReactionsCount());

            if (!userId.equals(post.getUserId())) {
                notificationEventProducer.publishPostReactionCreated(userId, post.getUserId(), postId);
            } else {
                log.debug("reactToPost: actor is post owner, skip notification postId={}", postId);
            }
        } else {
            ReactionsEntity reaction = existing.get();
            if (!resolvedType.equals(reaction.getType())) {
                reaction.setType(resolvedType);
                reactionsRepository.save(reaction);
                log.debug("reactToPost: type updated userId={} postId={} newType={}, skip notification", userId, postId, resolvedType);
            } else {
                log.debug("reactToPost: userId={} already reacted postId={} with same type={}, skip", userId, postId, resolvedType);
            }
        }

        return buildSummary(post, true, resolvedType);
    }

    public ReactionResponse removeReaction(String userId, Long postId) {
        log.debug("removeReaction: userId={} postId={}", userId, postId);
        PostEntity post = getActivePost(postId);

        boolean reactedByCurrentUser = reactionsRepository.existsByUserIdAndPostId(userId, postId);
        if (reactedByCurrentUser) {
            reactionsRepository.deleteByUserIdAndPostId(userId, postId);
            post.setReactionsCount(Math.max(0, post.getReactionsCount() - 1));
            postRepository.save(post);
            log.debug("removeReaction: reaction removed, postId={} totalReacts={}", postId, post.getReactionsCount());
        } else {
            log.debug("removeReaction: no reaction found for userId={} postId={}, skip", userId, postId);
        }

        return buildSummary(post, false, null);
    }

    @Transactional(readOnly = true)
    public ReactionResponse getReactionSummary(String userId, Long postId) {
        PostEntity post = getActivePost(postId);
        java.util.Optional<ReactionsEntity> existing = reactionsRepository.findByUserIdAndPostId(userId, postId);
        boolean reacted = existing.isPresent();
        String type = reacted ? existing.get().getType() : null;
        return buildSummary(post, reacted, type);
    }

    private PostEntity getActivePost(Long postId) {
        PostEntity post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found with id: " + postId));

        if (post.isDeleted()) {
            throw new IllegalStateException("Post was deleted");
        }

        return post;
    }

    private String resolveType(String type) {
        if (type == null || type.isBlank()) {
            return DEFAULT_REACTION_TYPE;
        }
        String normalized = type.trim().toUpperCase();
        return switch (normalized) {
            case "LIKE", "LOVE", "HAHA", "WOW", "SAD", "ANGRY" -> normalized;
            default -> throw new IllegalArgumentException("Invalid reaction type: " + type);
        };
    }

    private ReactionResponse buildSummary(PostEntity post, boolean reactedByCurrentUser, String reactionType) {
        return ReactionResponse.builder()
                .postId(post.getId())
                .totalReacts(post.getReactionsCount())
                .reactedByCurrentUser(reactedByCurrentUser)
                .reactionType(reactionType)
                .build();
    }
}
