package project.lms_rikkei_edu.modules.forum.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import project.lms_rikkei_edu.modules.forum.dto.request.AdminForumReportReviewRequest;
import project.lms_rikkei_edu.modules.forum.dto.response.AdminForumPostResponse;
import project.lms_rikkei_edu.modules.forum.dto.response.AdminForumReportResponse;
import project.lms_rikkei_edu.modules.forum.dto.response.ForumAdminPageResponse;
import project.lms_rikkei_edu.modules.forum.service.AdminForumService;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/forum")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminForumController {

    private final AdminForumService adminForumService;

    @GetMapping("/posts")
    public ResponseEntity<ForumAdminPageResponse<AdminForumPostResponse>> getPosts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean reportedOnly,
            @RequestParam(required = false) Boolean includeDeleted,
            @PageableDefault(size = 10) Pageable pageable) {
        Page<AdminForumPostResponse> page = adminForumService.getPosts(keyword, reportedOnly, includeDeleted, pageable);
        return ResponseEntity.ok(ForumAdminPageResponse.from(page));
    }

    @GetMapping("/reports")
    public ResponseEntity<ForumAdminPageResponse<AdminForumReportResponse>> getReports(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String targetType,
            @PageableDefault(size = 10) Pageable pageable) {
        Page<AdminForumReportResponse> page = adminForumService.getReports(status, targetType, pageable);
        return ResponseEntity.ok(ForumAdminPageResponse.from(page));
    }

    @PatchMapping("/reports/{reportId}")
    public ResponseEntity<Void> reviewReport(
            @PathVariable UUID reportId,
            @Valid @RequestBody AdminForumReportReviewRequest request) {
        adminForumService.reviewReport(reportId, request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/posts/{postId}")
    public ResponseEntity<Void> deletePost(@PathVariable UUID postId) {
        adminForumService.deletePost(postId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/replies/{replyId}")
    public ResponseEntity<Void> deleteReply(@PathVariable UUID replyId) {
        adminForumService.deleteReply(replyId);
        return ResponseEntity.noContent().build();
    }
}
