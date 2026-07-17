package project.lms_rikkei_edu.modules.forum.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import project.lms_rikkei_edu.modules.forum.dto.request.AdminForumReportReviewRequest;
import project.lms_rikkei_edu.modules.forum.dto.response.AdminForumPostResponse;
import project.lms_rikkei_edu.modules.forum.dto.response.AdminForumReportResponse;

import java.util.UUID;

public interface AdminForumService {
    Page<AdminForumPostResponse> getPosts(String keyword, Boolean reportedOnly, Boolean includeDeleted, Pageable pageable);

    Page<AdminForumReportResponse> getReports(String status, String targetType, Pageable pageable);

    void reviewReport(UUID reportId, AdminForumReportReviewRequest request);

    void deletePost(UUID postId);

    void deleteReply(UUID replyId);
}
