package com.socialmedia.social_media_social_service.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.socialmedia.social_media_social_service.dto.ReactionDTO.ReactionSummaryResponse;
import com.socialmedia.social_media_social_service.entities.PostEntity;
import com.socialmedia.social_media_social_service.entities.ReactionsEntity;
import com.socialmedia.social_media_social_service.exceptions.ResourceNotFoundException;
import com.socialmedia.social_media_social_service.repositories.PostRepository;
import com.socialmedia.social_media_social_service.repositories.ReactionsRepository;

import lombok.AllArgsConstructor;

@Service
@Transactional
@AllArgsConstructor
public class ReactionService {

    private static final String DEFAULT_REACTION_TYPE = "REACT";

    private final PostRepository postRepository;
    private final ReactionsRepository reactionsRepository;

    public ReactionSummaryResponse reactToPost(String userId, Long postId) {
        PostEntity post = getActivePost(postId);

        boolean reactedByCurrentUser = reactionsRepository.existsByUserIdAndPostId(userId, postId);
        if (!reactedByCurrentUser) {
            ReactionsEntity reaction = new ReactionsEntity();
            reaction.setUserId(userId);
            reaction.setPost(post);
            reaction.setType(DEFAULT_REACTION_TYPE);
            reactionsRepository.save(reaction);

            post.setReactionsCount(post.getReactionsCount() + 1);
            postRepository.save(post);
            reactedByCurrentUser = true;
        }

        return buildSummary(post, reactedByCurrentUser);
    }

    public ReactionSummaryResponse removeReaction(String userId, Long postId) {
        PostEntity post = getActivePost(postId);

        boolean reactedByCurrentUser = reactionsRepository.existsByUserIdAndPostId(userId, postId);
        if (reactedByCurrentUser) {
            reactionsRepository.deleteByUserIdAndPostId(userId, postId);
            post.setReactionsCount(Math.max(0, post.getReactionsCount() - 1));
            postRepository.save(post);
            reactedByCurrentUser = false;
        }

        return buildSummary(post, reactedByCurrentUser);
    }

    @Transactional(readOnly = true)
    public ReactionSummaryResponse getReactionSummary(String userId, Long postId) {
        PostEntity post = getActivePost(postId);
        boolean reactedByCurrentUser = reactionsRepository.existsByUserIdAndPostId(userId, postId);
        return buildSummary(post, reactedByCurrentUser);
    }

    private PostEntity getActivePost(Long postId) {
        PostEntity post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found with id: " + postId));

        if (post.isDeleted()) {
            throw new IllegalStateException("Post was deleted");
        }

        return post;
    }

    private ReactionSummaryResponse buildSummary(PostEntity post, boolean reactedByCurrentUser) {
        return ReactionSummaryResponse.builder()
                .postId(post.getId())
                .totalReacts(post.getReactionsCount())
                .reactedByCurrentUser(reactedByCurrentUser)
                .build();
    }
}
