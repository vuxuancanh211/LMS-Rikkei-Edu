package project.lms_rikkei_edu.modules.forum.service.impl;

import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.lms_rikkei_edu.common.exception.BusinessException;
import project.lms_rikkei_edu.common.security.CurrentUserProvider;
import project.lms_rikkei_edu.modules.forum.dto.request.AdminForumReportReviewRequest;
import project.lms_rikkei_edu.modules.forum.dto.response.AdminForumPostResponse;
import project.lms_rikkei_edu.modules.forum.dto.response.AdminForumReportResponse;
import project.lms_rikkei_edu.modules.forum.dto.response.ForumAuthorResponse;
import project.lms_rikkei_edu.modules.forum.entity.ForumPostEntity;
import project.lms_rikkei_edu.modules.forum.entity.ForumReportEntity;
import project.lms_rikkei_edu.modules.forum.entity.ForumReplyEntity;
import project.lms_rikkei_edu.modules.forum.repository.ForumPostRepository;
import project.lms_rikkei_edu.modules.forum.repository.ForumReplyRepository;
import project.lms_rikkei_edu.modules.forum.repository.ForumReportCountProjection;
import project.lms_rikkei_edu.modules.forum.repository.ForumReportRepository;
import project.lms_rikkei_edu.modules.forum.service.AdminForumService;
import project.lms_rikkei_edu.modules.user.entity.UserEntity;
import project.lms_rikkei_edu.modules.user.repository.UserRepository;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminForumServiceImpl implements AdminForumService {

    private static final String POST = "POST";
    private static final String REPLY = "REPLY";
    private static final String PENDING = "PENDING";
    private static final String RESOLVED = "RESOLVED";
    private static final String DISMISSED = "DISMISSED";

    private final ForumPostRepository forumPostRepository;
    private final ForumReplyRepository forumReplyRepository;
    private final ForumReportRepository forumReportRepository;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;

    @Override
    @Transactional(readOnly = true)
    public Page<AdminForumPostResponse> getPosts(String keyword, Boolean reportedOnly, Boolean includeDeleted, Pageable pageable) {
        boolean includeDeletedValue = Boolean.TRUE.equals(includeDeleted);
        String normalizedKeyword = normalize(keyword);
        Page<ForumPostEntity> page = normalizedKeyword == null
                ? forumPostRepository.findAdminPosts(includeDeletedValue, pageable)
                : forumPostRepository.searchAdminPosts(normalizedKeyword, includeDeletedValue, pageable);

        Map<UUID, ReportCounts> reportCounts = reportCounts(POST, page.getContent().stream().map(ForumPostEntity::getId).toList());
        List<AdminForumPostResponse> items = page.getContent().stream()
                .map(post -> toPostResponse(post, reportCounts.getOrDefault(post.getId(), ReportCounts.ZERO)))
                .filter(post -> !Boolean.TRUE.equals(reportedOnly) || post.getReportCount() > 0)
                .toList();

        return new PageImpl<>(items, pageable, Boolean.TRUE.equals(reportedOnly) ? items.size() : page.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AdminForumReportResponse> getReports(String status, String targetType, Pageable pageable) {
        String normalizedStatus = normalizeStatus(status, false);
        String normalizedTargetType = normalizeTargetType(targetType, false);
        Page<ForumReportEntity> page = forumReportRepository.findAdminReports(normalizedStatus, normalizedTargetType, pageable);

        Map<UUID, UserEntity> reporters = userRepository.findAllById(page.getContent().stream().map(ForumReportEntity::getReporterId).toList())
                .stream().collect(Collectors.toMap(UserEntity::getId, Function.identity()));
        Map<UUID, ForumPostEntity> posts = loadPosts(page.getContent().stream()
                .filter(r -> POST.equals(r.getTargetType()))
                .map(ForumReportEntity::getTargetId)
                .toList());
        Map<UUID, ForumReplyEntity> replies = loadReplies(page.getContent().stream()
                .filter(r -> REPLY.equals(r.getTargetType()))
                .map(ForumReportEntity::getTargetId)
                .toList());

        return page.map(report -> toReportResponse(report, reporters.get(report.getReporterId()), posts, replies));
    }

    @Override
    @Transactional
    public void reviewReport(UUID reportId, AdminForumReportReviewRequest request) {
        ForumReportEntity report = forumReportRepository.findById(reportId)
                .orElseThrow(() -> new BusinessException("Report not found", HttpStatus.NOT_FOUND));
        String status = normalizeStatus(request.getStatus(), true);
        if (!RESOLVED.equals(status) && !DISMISSED.equals(status)) {
            throw new BusinessException("Invalid report status");
        }

        if (request.isDeleteTarget()) {
            if (POST.equals(report.getTargetType())) deletePost(report.getTargetId());
            else if (REPLY.equals(report.getTargetType())) deleteReply(report.getTargetId());
        }

        report.setStatus(status);
        report.setReviewedBy(currentUserId());
        report.setReviewedAt(OffsetDateTime.now(ZoneId.systemDefault()));
    }

    @Override
    @Transactional
    public void deletePost(UUID postId) {
        ForumPostEntity post = forumPostRepository.findById(postId)
                .orElseThrow(() -> new BusinessException("Post not found", HttpStatus.NOT_FOUND));
        if (Boolean.TRUE.equals(post.getDeleted())) return;
        post.setDeleted(true);
        post.setDeletedBy(currentUserId());
        post.setDeletedAt(OffsetDateTime.now(ZoneId.systemDefault()));
        post.setUpdatedAt(post.getDeletedAt());
    }

    @Override
    @Transactional
    public void deleteReply(UUID replyId) {
        ForumReplyEntity reply = forumReplyRepository.findById(replyId)
                .orElseThrow(() -> new BusinessException("Reply not found", HttpStatus.NOT_FOUND));
        if (Boolean.TRUE.equals(reply.getDeleted())) return;
        OffsetDateTime now = OffsetDateTime.now(ZoneId.systemDefault());
        reply.setDeleted(true);
        reply.setDeletedBy(currentUserId());
        reply.setDeletedAt(now);
        reply.setUpdatedAt(now);
        ForumPostEntity post = reply.getPost();
        post.setReplyCount(Math.max(0, nullToZero(post.getReplyCount()) - 1));
        post.setUpdatedAt(now);
    }

    private AdminForumPostResponse toPostResponse(ForumPostEntity post, ReportCounts counts) {
        return AdminForumPostResponse.builder()
                .id(post.getId())
                .courseId(post.getCourse().getId())
                .courseTitle(post.getCourse().getTitle())
                .author(toAuthor(post.getAuthor()))
                .topic(post.getTopic())
                .title(post.getTitle())
                .contentPreview(preview(post.getContent()))
                .pinned(Boolean.TRUE.equals(post.getPinned()))
                .replyCount(nullToZero(post.getReplyCount()))
                .upvoteCount(nullToZero(post.getUpvoteCount()))
                .deleted(Boolean.TRUE.equals(post.getDeleted()))
                .reportCount((int) counts.total())
                .pendingReportCount((int) counts.pending())
                .createdAt(post.getCreatedAt())
                .updatedAt(post.getUpdatedAt())
                .deletedAt(post.getDeletedAt())
                .build();
    }

    private AdminForumReportResponse toReportResponse(ForumReportEntity report, UserEntity reporter,
                                                      Map<UUID, ForumPostEntity> posts,
                                                      Map<UUID, ForumReplyEntity> replies) {
        ForumPostEntity post = null;
        ForumReplyEntity reply = null;
        if (POST.equals(report.getTargetType())) post = posts.get(report.getTargetId());
        if (REPLY.equals(report.getTargetType())) {
            reply = replies.get(report.getTargetId());
            post = reply != null ? reply.getPost() : null;
        }

        return AdminForumReportResponse.builder()
                .id(report.getId())
                .targetType(report.getTargetType())
                .targetId(report.getTargetId())
                .targetTitle(post != null ? post.getTitle() : "Nội dung đã bị xóa")
                .targetContentPreview(reply != null ? preview(reply.getContent()) : post != null ? preview(post.getContent()) : "")
                .postId(post != null ? post.getId() : null)
                .courseTitle(post != null ? post.getCourse().getTitle() : null)
                .reason(report.getReason())
                .description(report.getDescription())
                .status(report.getStatus())
                .reporter(toAuthor(reporter))
                .createdAt(report.getCreatedAt())
                .reviewedBy(report.getReviewedBy())
                .reviewedAt(report.getReviewedAt())
                .targetDeleted(reply != null ? Boolean.TRUE.equals(reply.getDeleted()) : post != null && Boolean.TRUE.equals(post.getDeleted()))
                .build();
    }

    private Map<UUID, ReportCounts> reportCounts(String targetType, Collection<UUID> targetIds) {
        if (targetIds == null || targetIds.isEmpty()) return Collections.emptyMap();
        Map<UUID, ReportCounts> result = new HashMap<>();
        for (ForumReportCountProjection projection : forumReportRepository.countReportsByTargets(targetType, targetIds)) {
            result.put(projection.getTargetId(), new ReportCounts(projection.getTotalCount(), projection.getPendingCount()));
        }
        return result;
    }

    private Map<UUID, ForumPostEntity> loadPosts(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) return Collections.emptyMap();
        return forumPostRepository.findByIdInWithRelations(ids).stream().collect(Collectors.toMap(ForumPostEntity::getId, Function.identity()));
    }

    private Map<UUID, ForumReplyEntity> loadReplies(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) return Collections.emptyMap();
        return forumReplyRepository.findByIdInWithRelations(ids).stream().collect(Collectors.toMap(ForumReplyEntity::getId, Function.identity()));
    }

    private ForumAuthorResponse toAuthor(UserEntity user) {
        if (user == null) return null;
        return ForumAuthorResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .role(user.getRole() == null ? null : user.getRole().name())
                .avatarUrl(user.getAvatarUrl())
                .build();
    }

    private UUID currentUserId() {
        return currentUserProvider.getCurrentUserId()
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("User is not authenticated"));
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private String normalizeStatus(String value, boolean required) {
        String normalized = normalize(value);
        if (normalized == null) return required ? PENDING : null;
        return normalized.toUpperCase();
    }

    private String normalizeTargetType(String value, boolean required) {
        String normalized = normalize(value);
        if (normalized == null) return required ? POST : null;
        normalized = normalized.toUpperCase();
        if (!POST.equals(normalized) && !REPLY.equals(normalized)) throw new BusinessException("Invalid target type");
        return normalized;
    }

    private String preview(String html) {
        String text = Jsoup.parse(html == null ? "" : html).text().replaceAll("\\s+", " ").trim();
        return text.length() <= 180 ? text : text.substring(0, 177) + "...";
    }

    private int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }

    private record ReportCounts(long total, long pending) {
        private static final ReportCounts ZERO = new ReportCounts(0, 0);
    }
}
