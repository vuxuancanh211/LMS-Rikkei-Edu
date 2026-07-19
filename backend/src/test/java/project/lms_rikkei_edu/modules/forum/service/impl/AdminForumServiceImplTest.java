package project.lms_rikkei_edu.modules.forum.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import project.lms_rikkei_edu.common.exception.BusinessException;
import project.lms_rikkei_edu.common.security.CurrentUserProvider;
import project.lms_rikkei_edu.modules.forum.dto.request.AdminForumReportReviewRequest;
import project.lms_rikkei_edu.modules.forum.entity.ForumCourseEntity;
import project.lms_rikkei_edu.modules.forum.entity.ForumPostEntity;
import project.lms_rikkei_edu.modules.forum.entity.ForumReportEntity;
import project.lms_rikkei_edu.modules.forum.entity.ForumReplyEntity;
import project.lms_rikkei_edu.modules.forum.repository.ForumPostRepository;
import project.lms_rikkei_edu.modules.forum.repository.ForumReplyRepository;
import project.lms_rikkei_edu.modules.forum.repository.ForumReportCountProjection;
import project.lms_rikkei_edu.modules.forum.repository.ForumReportRepository;
import project.lms_rikkei_edu.modules.user.entity.UserEntity;
import project.lms_rikkei_edu.modules.user.enums.UserRole;
import project.lms_rikkei_edu.modules.user.repository.UserRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminForumServiceImplTest {

    @Mock
    private ForumPostRepository forumPostRepository;
    @Mock
    private ForumReplyRepository forumReplyRepository;
    @Mock
    private ForumReportRepository forumReportRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CurrentUserProvider currentUserProvider;

    private AdminForumServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminForumServiceImpl(
                forumPostRepository,
                forumReplyRepository,
                forumReportRepository,
                userRepository,
                currentUserProvider
        );
    }

    @Test
    void getPostsReturnsAdminPostResponsesWithReportCounts() {
        var pageable = PageRequest.of(0, 10);
        ForumPostEntity post = post(UUID.randomUUID(), false, 2);
        when(forumPostRepository.findAdminPosts(false, pageable)).thenReturn(new PageImpl<>(List.of(post), pageable, 1));
        when(forumReportRepository.countReportsByTargets(eq("POST"), any()))
                .thenReturn(List.of(reportCount(post.getId(), 3, 2)));

        var page = service.getPosts(" ", false, false, pageable);

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent()).hasSize(1);
        var response = page.getContent().get(0);
        assertThat(response.getId()).isEqualTo(post.getId());
        assertThat(response.getCourseTitle()).isEqualTo("Java Spring");
        assertThat(response.getAuthor().getFullName()).isEqualTo("Forum Author");
        assertThat(response.getContentPreview()).isEqualTo("Hello forum content");
        assertThat(response.getReplyCount()).isEqualTo(2);
        assertThat(response.getReportCount()).isEqualTo(3);
        assertThat(response.getPendingReportCount()).isEqualTo(2);
        verify(forumPostRepository, never()).searchAdminPosts(anyString(), anyBoolean(), any());
    }

    @Test
    void getPostsSearchesAndFiltersReportedOnly() {
        var pageable = PageRequest.of(0, 10);
        ForumPostEntity reported = post(UUID.randomUUID(), false, 0);
        ForumPostEntity clean = post(UUID.randomUUID(), false, 0);
        when(forumPostRepository.searchAdminPosts("spring", true, pageable))
                .thenReturn(new PageImpl<>(List.of(reported, clean), pageable, 2));
        when(forumReportRepository.countReportsByTargets(eq("POST"), any()))
                .thenReturn(List.of(reportCount(reported.getId(), 1, 1)));

        var page = service.getPosts(" spring ", true, true, pageable);

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent()).extracting("id").containsExactly(reported.getId());
    }

    @Test
    void getReportsMapsPostAndReplyTargets() {
        var pageable = PageRequest.of(0, 10);
        ForumPostEntity post = post(UUID.randomUUID(), false, 5);
        ForumReplyEntity reply = reply(UUID.randomUUID(), post, false);
        ForumReportEntity postReport = report(UUID.randomUUID(), "POST", post.getId(), "PENDING");
        ForumReportEntity replyReport = report(UUID.randomUUID(), "REPLY", reply.getId(), "RESOLVED");
        UserEntity reporter = user(postReport.getReporterId(), "Reporter", UserRole.STUDENT);

        when(forumReportRepository.findAdminReports("PENDING", "POST", pageable))
                .thenReturn(new PageImpl<>(List.of(postReport, replyReport), pageable, 2));
        when(userRepository.findAllById(List.of(postReport.getReporterId(), replyReport.getReporterId())))
                .thenReturn(List.of(reporter));
        when(forumPostRepository.findByIdInWithRelations(List.of(post.getId()))).thenReturn(List.of(post));
        when(forumReplyRepository.findByIdInWithRelations(List.of(reply.getId()))).thenReturn(List.of(reply));

        var page = service.getReports(" pending ", " post ", pageable);

        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getContent().get(0).getTargetTitle()).isEqualTo(post.getTitle());
        assertThat(page.getContent().get(0).getTargetContentPreview()).isEqualTo("Hello forum content");
        assertThat(page.getContent().get(0).getReporter().getFullName()).isEqualTo("Reporter");
        assertThat(page.getContent().get(1).getPostId()).isEqualTo(post.getId());
        assertThat(page.getContent().get(1).getTargetContentPreview()).isEqualTo("Reply content");
    }

    @Test
    void getReportsHandlesMissingTargetAndReporter() {
        var pageable = PageRequest.of(0, 10);
        ForumReportEntity report = report(UUID.randomUUID(), "POST", UUID.randomUUID(), "PENDING");
        when(forumReportRepository.findAdminReports(null, null, pageable))
                .thenReturn(new PageImpl<>(List.of(report), pageable, 1));
        when(userRepository.findAllById(List.of(report.getReporterId()))).thenReturn(List.of());
        when(forumPostRepository.findByIdInWithRelations(List.of(report.getTargetId()))).thenReturn(List.of());

        var response = service.getReports(null, null, pageable).getContent().get(0);

        assertThat(response.getTargetTitle()).isEqualTo("Nội dung đã bị xóa");
        assertThat(response.getTargetContentPreview()).isEmpty();
        assertThat(response.getReporter()).isNull();
        assertThat(response.getCourseTitle()).isNull();
    }

    @Test
    void getReportsRejectsInvalidTargetType() {
        assertThatThrownBy(() -> service.getReports(null, "USER", PageRequest.of(0, 10)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Invalid target type");
    }

    @Test
    void reviewReportResolvesAndDeletesPostTarget() {
        UUID adminId = UUID.randomUUID();
        ForumPostEntity post = post(UUID.randomUUID(), false, 0);
        ForumReportEntity report = report(UUID.randomUUID(), "POST", post.getId(), "PENDING");
        when(forumReportRepository.findById(report.getId())).thenReturn(Optional.of(report));
        when(forumPostRepository.findById(post.getId())).thenReturn(Optional.of(post));
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(adminId));

        AdminForumReportReviewRequest request = reviewRequest(" resolved ", true);
        service.reviewReport(report.getId(), request);

        assertThat(report.getStatus()).isEqualTo("RESOLVED");
        assertThat(report.getReviewedBy()).isEqualTo(adminId);
        assertThat(report.getReviewedAt()).isNotNull();
        assertThat(post.getDeleted()).isTrue();
        assertThat(post.getDeletedBy()).isEqualTo(adminId);
        assertThat(post.getUpdatedAt()).isEqualTo(post.getDeletedAt());
    }

    @Test
    void reviewReportDismissesWithoutDeletingTarget() {
        UUID adminId = UUID.randomUUID();
        ForumReportEntity report = report(UUID.randomUUID(), "POST", UUID.randomUUID(), "PENDING");
        when(forumReportRepository.findById(report.getId())).thenReturn(Optional.of(report));
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(adminId));

        service.reviewReport(report.getId(), reviewRequest("DISMISSED", false));

        assertThat(report.getStatus()).isEqualTo("DISMISSED");
        verify(forumPostRepository, never()).findById(any());
        verify(forumReplyRepository, never()).findById(any());
    }

    @Test
    void reviewReportRejectsInvalidStatus() {
        ForumReportEntity report = report(UUID.randomUUID(), "POST", UUID.randomUUID(), "PENDING");
        when(forumReportRepository.findById(report.getId())).thenReturn(Optional.of(report));

        assertThatThrownBy(() -> service.reviewReport(report.getId(), reviewRequest("PENDING", false)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Invalid report status");
    }

    @Test
    void reviewReportThrowsWhenReportMissing() {
        UUID reportId = UUID.randomUUID();
        when(forumReportRepository.findById(reportId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reviewReport(reportId, reviewRequest("RESOLVED", false)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Report not found");
    }

    @Test
    void deletePostMarksActivePostDeleted() {
        UUID adminId = UUID.randomUUID();
        ForumPostEntity post = post(UUID.randomUUID(), false, 0);
        when(forumPostRepository.findById(post.getId())).thenReturn(Optional.of(post));
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(adminId));

        service.deletePost(post.getId());

        assertThat(post.getDeleted()).isTrue();
        assertThat(post.getDeletedBy()).isEqualTo(adminId);
        assertThat(post.getDeletedAt()).isNotNull();
    }

    @Test
    void deletePostIgnoresAlreadyDeletedPost() {
        ForumPostEntity post = post(UUID.randomUUID(), true, 0);
        when(forumPostRepository.findById(post.getId())).thenReturn(Optional.of(post));

        service.deletePost(post.getId());

        verify(currentUserProvider, never()).getCurrentUserId();
    }

    @Test
    void deletePostThrowsWhenMissing() {
        UUID postId = UUID.randomUUID();
        when(forumPostRepository.findById(postId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deletePost(postId))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Post not found");
    }

    @Test
    void deleteReplyMarksReplyDeletedAndDecrementsPostReplyCount() {
        UUID adminId = UUID.randomUUID();
        ForumPostEntity post = post(UUID.randomUUID(), false, 1);
        ForumReplyEntity reply = reply(UUID.randomUUID(), post, false);
        when(forumReplyRepository.findById(reply.getId())).thenReturn(Optional.of(reply));
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(adminId));

        service.deleteReply(reply.getId());

        assertThat(reply.getDeleted()).isTrue();
        assertThat(reply.getDeletedBy()).isEqualTo(adminId);
        assertThat(reply.getUpdatedAt()).isEqualTo(reply.getDeletedAt());
        assertThat(post.getReplyCount()).isZero();
        assertThat(post.getUpdatedAt()).isEqualTo(reply.getDeletedAt());
    }

    @Test
    void deleteReplyDoesNotMakeReplyCountNegative() {
        ForumPostEntity post = post(UUID.randomUUID(), false, null);
        ForumReplyEntity reply = reply(UUID.randomUUID(), post, false);
        when(forumReplyRepository.findById(reply.getId())).thenReturn(Optional.of(reply));
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(UUID.randomUUID()));

        service.deleteReply(reply.getId());

        assertThat(post.getReplyCount()).isZero();
    }

    @Test
    void deleteReplyIgnoresAlreadyDeletedReply() {
        ForumReplyEntity reply = reply(UUID.randomUUID(), post(UUID.randomUUID(), false, 3), true);
        when(forumReplyRepository.findById(reply.getId())).thenReturn(Optional.of(reply));

        service.deleteReply(reply.getId());

        verify(currentUserProvider, never()).getCurrentUserId();
    }

    @Test
    void deleteReplyThrowsWhenMissing() {
        UUID replyId = UUID.randomUUID();
        when(forumReplyRepository.findById(replyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteReply(replyId))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Reply not found");
    }

    @Test
    void deletePostRequiresAuthenticatedAdmin() {
        ForumPostEntity post = post(UUID.randomUUID(), false, 0);
        when(forumPostRepository.findById(post.getId())).thenReturn(Optional.of(post));
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deletePost(post.getId()))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    private AdminForumReportReviewRequest reviewRequest(String status, boolean deleteTarget) {
        AdminForumReportReviewRequest request = new AdminForumReportReviewRequest();
        request.setStatus(status);
        request.setDeleteTarget(deleteTarget);
        return request;
    }

    private ForumPostEntity post(UUID id, Boolean deleted, Integer replyCount) {
        ForumPostEntity post = new ForumPostEntity();
        post.setId(id);
        post.setCourse(course(UUID.randomUUID(), "Java Spring"));
        post.setAuthor(user(UUID.randomUUID(), "Forum Author", UserRole.STUDENT));
        post.setTopic("qa");
        post.setTitle("How to test forum?");
        post.setContent("<p>Hello <strong>forum</strong> content</p>");
        post.setPinned(false);
        post.setReplyCount(replyCount);
        post.setUpvoteCount(null);
        post.setDeleted(deleted);
        post.setCreatedAt(OffsetDateTime.now().minusDays(1));
        post.setUpdatedAt(OffsetDateTime.now());
        return post;
    }

    private ForumReplyEntity reply(UUID id, ForumPostEntity post, Boolean deleted) {
        ForumReplyEntity reply = new ForumReplyEntity();
        reply.setId(id);
        reply.setPost(post);
        reply.setCourse(post.getCourse());
        reply.setAuthor(user(UUID.randomUUID(), "Reply Author", UserRole.INSTRUCTOR));
        reply.setContent("<p>Reply content</p>");
        reply.setDeleted(deleted);
        reply.setUpvoteCount(0);
        reply.setCreatedAt(OffsetDateTime.now().minusHours(2));
        reply.setUpdatedAt(OffsetDateTime.now().minusHours(1));
        return reply;
    }

    private ForumReportEntity report(UUID id, String targetType, UUID targetId, String status) {
        ForumReportEntity report = new ForumReportEntity();
        report.setId(id);
        report.setTargetType(targetType);
        report.setTargetId(targetId);
        report.setReporterId(UUID.randomUUID());
        report.setReason("SPAM");
        report.setDescription("Looks like spam");
        report.setStatus(status);
        report.setCreatedAt(OffsetDateTime.now().minusMinutes(30));
        return report;
    }

    private ForumCourseEntity course(UUID id, String title) {
        ForumCourseEntity course = new ForumCourseEntity();
        course.setId(id);
        course.setTitle(title);
        return course;
    }

    private UserEntity user(UUID id, String fullName, UserRole role) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setFullName(fullName);
        user.setRole(role);
        user.setAvatarUrl("avatar.png");
        return user;
    }

    private ForumReportCountProjection reportCount(UUID targetId, long total, long pending) {
        return new ForumReportCountProjection() {
            @Override
            public UUID getTargetId() {
                return targetId;
            }

            @Override
            public long getTotalCount() {
                return total;
            }

            @Override
            public long getPendingCount() {
                return pending;
            }
        };
    }
}
