package project.lms_rikkei_edu.modules.forum.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import project.lms_rikkei_edu.modules.forum.dto.request.CreateForumPostRequest;
import project.lms_rikkei_edu.modules.forum.dto.request.CreateForumReplyRequest;
import project.lms_rikkei_edu.modules.forum.dto.request.UpdateForumPostRequest;
import project.lms_rikkei_edu.modules.forum.dto.response.ForumPostDetailResponse;
import project.lms_rikkei_edu.modules.forum.dto.response.ForumCourseResponse;
import project.lms_rikkei_edu.modules.forum.dto.response.ForumPostResponse;
import project.lms_rikkei_edu.modules.forum.dto.response.ForumReplyResponse;

import java.util.UUID;
import java.util.List;

public interface ForumService {
    List<ForumCourseResponse> getCourses();

    Page<ForumPostResponse> getPosts(UUID courseId, String keyword, Pageable pageable);

    ForumPostDetailResponse getPostDetail(UUID postId);

    ForumPostResponse createPost(CreateForumPostRequest request);

    ForumReplyResponse createReply(UUID postId, CreateForumReplyRequest request);

    ForumPostResponse updatePost(UUID postId, UpdateForumPostRequest request);

    ForumReplyResponse updateReply(UUID replyId, CreateForumReplyRequest request);

    void deletePost(UUID postId);

    void deleteReply(UUID replyId);
}
