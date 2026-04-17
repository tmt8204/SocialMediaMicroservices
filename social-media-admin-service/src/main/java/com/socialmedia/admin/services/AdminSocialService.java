package com.socialmedia.admin.services;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.socialmedia.admin.entities.jpa.CommentEntity;
import com.socialmedia.admin.entities.jpa.CommunityEntity;
import com.socialmedia.admin.entities.jpa.PostEntity;
import com.socialmedia.admin.repositories.jpa.CommentAdminRepository;
import com.socialmedia.admin.repositories.jpa.CommunityAdminRepository;
import com.socialmedia.admin.repositories.jpa.PostAdminRepository;

@Service
public class AdminSocialService {

    private final PostAdminRepository postRepository;
    private final CommentAdminRepository commentRepository;
    private final CommunityAdminRepository communityRepository;

    public AdminSocialService(PostAdminRepository postRepository,
                              CommentAdminRepository commentRepository,
                              CommunityAdminRepository communityRepository) {
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.communityRepository = communityRepository;
    }

    // ===== POSTS =====
    public List<PostEntity> getAllPosts() {
        return postRepository.findAll();
    }

    public long countPosts() {
        return postRepository.count();
    }

    @Transactional
    public void deletePost(Long postId) {
        if (!postRepository.existsById(postId)) {
            throw new IllegalArgumentException("Post not found: " + postId);
        }
        postRepository.deleteById(postId);
    }

    // ===== COMMENTS =====
    public List<CommentEntity> getAllComments() {
        return commentRepository.findAll();
    }

    public long countComments() {
        return commentRepository.count();
    }

    @Transactional
    public void deleteComment(Long commentId) {
        if (!commentRepository.existsById(commentId)) {
            throw new IllegalArgumentException("Comment not found: " + commentId);
        }
        commentRepository.deleteById(commentId);
    }

    // ===== COMMUNITIES =====
    public List<CommunityEntity> getAllCommunities() {
        return communityRepository.findAll();
    }

    public long countCommunities() {
        return communityRepository.count();
    }

    @Transactional
    public void deleteCommunity(Long communityId) {
        if (!communityRepository.existsById(communityId)) {
            throw new IllegalArgumentException("Community not found: " + communityId);
        }
        communityRepository.deleteById(communityId);
    }
}
