package com.socialmedia.social_media_social_service.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.socialmedia.social_media_social_service.dto.PostCreateRequest;
import com.socialmedia.social_media_social_service.dto.PostResponse;
import com.socialmedia.social_media_social_service.dto.PostUpdateRequest;
import com.socialmedia.social_media_social_service.entities.PostEntity;
import com.socialmedia.social_media_social_service.entities.PostMedia;
import com.socialmedia.social_media_social_service.exceptions.ResourceNotFoundException;
import com.socialmedia.social_media_social_service.helpers.PostHelper;
import com.socialmedia.social_media_social_service.repositories.FriendRepository;
import com.socialmedia.social_media_social_service.repositories.PostRepository;

import lombok.AllArgsConstructor;

@Service
@Transactional
@AllArgsConstructor
public class PostService {

    private final PostRepository postRepository;

    @Autowired
    private final FriendRepository friendRepository;

    @Autowired
    private final PostHelper postHelper;

    //---------------- POST OPERATIONS ----------------
    public PostResponse createPost(String userId, PostCreateRequest request) {
        PostEntity post = new PostEntity();
        post.setUserId(userId);
        post.setContent(request.getContent());
        post.setVisibility(postHelper.resolveVisibility(request.getVisibility()));
        post.setCreatedAt(new Date());
        post.setUpdatedAt(new Date());

        replacePostMedia(post, request.getMediaUrls());
        PostEntity savedPost = postRepository.save(post);

        return postHelper.convertToPostResponse(savedPost);
    }

    public PostResponse updatePost(String userId, Long postId, PostUpdateRequest request) {

        // Ensure the post exists and belongs to the user
        PostEntity post = postRepository.findByIdAndUserId(postId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found with id: " + postId));

        post.setContent(request.getContent());
        post.setVisibility(postHelper.resolveVisibility(request.getVisibility()));
        post.setUpdatedAt(new Date());

        replacePostMedia(post, request.getMediaUrls());

        PostEntity updatedPost = postRepository.save(post);

        return postHelper.convertToPostResponse(updatedPost);
    }

    public PostResponse getPostById(String userId, Long postId) {
        PostEntity post = postRepository.findByIdAndUserId(postId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found with id: " + postId + " for user: " + userId));

        return postHelper.convertToPostResponse(post);
    }

    public void deletePost(String userId, Long postId){
        PostEntity post = postRepository.findByIdAndUserId(postId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found with id: " + postId + " for user: " + userId));

        postRepository.delete(post);
    }
    
    //----------------NEWSFEED OPERATIONS----------------
    public Page<PostResponse> getFeed(String userId, Pageable pageable) {
        // Get accepted friends only (NOT including current user)
        List<String> friendIds = friendRepository.findAcceptedFriendsIds(userId);
        
        // Fetch posts from friends with pagination
        // Only shows friends' FRIEND and PUBLIC posts, plus PUBLIC posts from others
        Page<PostEntity> feedPosts = postRepository.findFeedPosts(userId, friendIds, pageable);
        
        return feedPosts.map(postHelper::convertToPostResponse);
    }
    
    public Page<PostResponse> getOwnerPost(String userId, Pageable pageable) {
        Page<PostEntity> userPosts = postRepository.findUserPosts(userId, pageable);
        return userPosts.map(postHelper::convertToPostResponse);
    }

    private void replacePostMedia(PostEntity post, List<String> mediaUrls) {
        if (post.getMedia() == null) {
            post.setMedia(new ArrayList<>());
        } else {
            post.getMedia().clear();
        }

        if (mediaUrls == null) {
            return;
        }

        for (String mediaUrl : mediaUrls) {
            if (mediaUrl == null || mediaUrl.isBlank()) {
                continue;
            }

            PostMedia item = new PostMedia();
            item.setPost(post);
            item.setMediaUrl(mediaUrl);
            post.getMedia().add(item);
        }
    }

}
