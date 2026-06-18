package project.lms_rikkei_edu.modules.forum.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import project.lms_rikkei_edu.modules.forum.dto.request.CreateForumPostRequest;
import project.lms_rikkei_edu.modules.forum.dto.request.CreateForumReplyRequest;
import project.lms_rikkei_edu.modules.forum.dto.request.UpdateForumPostRequest;
import project.lms_rikkei_edu.modules.forum.dto.response.ForumPostDetailResponse;
import project.lms_rikkei_edu.modules.forum.dto.response.ForumCourseResponse;
import project.lms_rikkei_edu.modules.forum.dto.response.ForumPostResponse;
import project.lms_rikkei_edu.modules.forum.dto.response.ForumReplyResponse;
import project.lms_rikkei_edu.modules.forum.service.ForumService;

import java.util.UUID;
import java.util.List;

@RestController
@RequestMapping("/api/forum")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class ForumController {

    private final ForumService forumService;

    @GetMapping("/courses")
    public ResponseEntity<List<ForumCourseResponse>> getCourses() {
        return ResponseEntity.ok(forumService.getCourses());
    }

    @GetMapping("/posts")
    public ResponseEntity<Page<ForumPostResponse>> getPosts(
            @RequestParam(required = false) UUID courseId,
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 10) Pageable pageable
    ) {
        return ResponseEntity.ok(forumService.getPosts(courseId, keyword, pageable));
    }

    @GetMapping("/posts/{postId}")
    public ResponseEntity<ForumPostDetailResponse> getPostDetail(@PathVariable UUID postId) {
        return ResponseEntity.ok(forumService.getPostDetail(postId));
    }

    @PostMapping("/posts")
    public ResponseEntity<ForumPostResponse> createPost(@Valid @RequestBody CreateForumPostRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(forumService.createPost(request));
    }

    @PostMapping("/posts/{postId}/replies")
    public ResponseEntity<ForumReplyResponse> createReply(
            @PathVariable UUID postId,
            @Valid @RequestBody CreateForumReplyRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(forumService.createReply(postId, request));
    }

    @PatchMapping("/posts/{postId}")
    public ResponseEntity<ForumPostResponse> updatePost(
            @PathVariable UUID postId,
            @Valid @RequestBody UpdateForumPostRequest request
    ) {
        return ResponseEntity.ok(forumService.updatePost(postId, request));
    }

    @PatchMapping("/replies/{replyId}")
    public ResponseEntity<ForumReplyResponse> updateReply(
            @PathVariable UUID replyId,
            @Valid @RequestBody CreateForumReplyRequest request
    ) {
        return ResponseEntity.ok(forumService.updateReply(replyId, request));
    }

    @DeleteMapping("/posts/{postId}")
    public ResponseEntity<Void> deletePost(@PathVariable UUID postId) {
        forumService.deletePost(postId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/replies/{replyId}")
    public ResponseEntity<Void> deleteReply(@PathVariable UUID replyId) {
        forumService.deleteReply(replyId);
        return ResponseEntity.noContent().build();
    }
}
