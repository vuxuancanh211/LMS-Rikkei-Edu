package project.lms_rikkei_edu.modules.forum.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import project.lms_rikkei_edu.common.exception.BusinessException;
import project.lms_rikkei_edu.common.security.CurrentUserProvider;
import project.lms_rikkei_edu.common.security.UserPrincipal;
import project.lms_rikkei_edu.modules.forum.dto.request.CreateForumPostRequest;
import project.lms_rikkei_edu.modules.forum.dto.request.CreateForumReplyRequest;
import project.lms_rikkei_edu.modules.forum.dto.request.CreateForumReportRequest;
import project.lms_rikkei_edu.modules.forum.entity.ForumCourseEntity;
import project.lms_rikkei_edu.modules.forum.entity.ForumPostEntity;
import project.lms_rikkei_edu.modules.forum.entity.ForumReactionEntity;
import project.lms_rikkei_edu.modules.forum.entity.ForumReportEntity;
import project.lms_rikkei_edu.modules.forum.entity.ForumReplyEntity;
import project.lms_rikkei_edu.modules.forum.repository.ForumCourseRepository;
import project.lms_rikkei_edu.modules.forum.repository.ForumPostRepository;
import project.lms_rikkei_edu.modules.forum.repository.ForumReactionRepository;
import project.lms_rikkei_edu.modules.forum.repository.ForumReportRepository;
import project.lms_rikkei_edu.modules.forum.repository.ForumReplyRepository;
import project.lms_rikkei_edu.modules.forum.service.impl.ForumServiceImpl;
import project.lms_rikkei_edu.modules.notification.service.NotificationService;
import project.lms_rikkei_edu.modules.user.entity.UserEntity;
import project.lms_rikkei_edu.modules.user.enums.UserRole;
import project.lms_rikkei_edu.modules.user.enums.UserStatus;
import project.lms_rikkei_edu.modules.user.repository.UserRepository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ForumServiceImplTest {

    @Mock
    private ForumPostRepository forumPostRepository;
    @Mock
    private ForumReplyRepository forumReplyRepository;
    @Mock
    private ForumCourseRepository forumCourseRepository;
    @Mock
    private ForumReactionRepository forumReactionRepository;
    @Mock
    private ForumReportRepository forumReportRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CurrentUserProvider currentUserProvider;

    private ForumServiceImpl forumService;

    @BeforeEach
    void setUp() {
        forumService = new ForumServiceImpl(
                forumPostRepository,
                forumReplyRepository,
                forumCourseRepository,
                forumReactionRepository,
                forumReportRepository,
                notificationService,
                userRepository,
                currentUserProvider
        );
    }

    @Test
    void createPostRejectsStudentNotEnrolled() {
        UUID studentId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();

        when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(principal(studentId, UserRole.STUDENT)));
        when(forumCourseRepository.findById(courseId)).thenReturn(Optional.of(course(courseId, UUID.randomUUID())));
        when(forumCourseRepository.isStudentEnrolled(courseId, studentId)).thenReturn(false);

        CreateForumPostRequest request = createPostRequest(courseId, false);

        assertThatThrownBy(() -> forumService.createPost(request))
                .isInstanceOf(BusinessException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.FORBIDDEN);
        verify(forumPostRepository, never()).save(any());
    }

    @Test
    void createPostAllowsEnrolledStudent() {
        UUID studentId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        ForumCourseEntity course = course(courseId, UUID.randomUUID());
        UserEntity user = user(studentId, UserRole.STUDENT);

        when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(principal(studentId, UserRole.STUDENT)));
        when(forumCourseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(forumCourseRepository.isStudentEnrolled(courseId, studentId)).thenReturn(true);
        when(userRepository.getReferenceById(studentId)).thenReturn(user);
        when(forumPostRepository.save(any(ForumPostEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        forumService.createPost(createPostRequest(courseId, false));

        ArgumentCaptor<ForumPostEntity> captor = ArgumentCaptor.forClass(ForumPostEntity.class);
        verify(forumPostRepository).save(captor.capture());
        assertThat(captor.getValue().getCourse()).isSameAs(course);
        assertThat(captor.getValue().getAuthor()).isSameAs(user);
        assertThat(captor.getValue().getPinned()).isFalse();
        assertThat(captor.getValue().getUpvoteCount()).isZero();
    }

    @Test
    void togglePinRejectsInstructorOutsideCourse() {
        UUID instructorId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();

        when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(principal(instructorId, UserRole.INSTRUCTOR)));
        when(forumPostRepository.findActiveById(postId)).thenReturn(Optional.of(post(postId, course(courseId, UUID.randomUUID()), user(UUID.randomUUID(), UserRole.STUDENT))));
        when(forumCourseRepository.isInstructorOfCourse(courseId, instructorId)).thenReturn(false);

        assertThatThrownBy(() -> forumService.togglePin(postId))
                .isInstanceOf(BusinessException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void togglePostUpvoteAddsAndRemovesReaction() {
        UUID userId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        ForumPostEntity post = post(postId, course(UUID.randomUUID(), UUID.randomUUID()), user(UUID.randomUUID(), UserRole.STUDENT));
        post.setUpvoteCount(0);

        when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(principal(userId, UserRole.STUDENT)));
        when(forumPostRepository.findActiveById(postId)).thenReturn(Optional.of(post));
        when(forumReactionRepository.findByPostIdAndUserId(postId, userId))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(new ForumReactionEntity()))
                .thenReturn(Optional.of(new ForumReactionEntity()))
                .thenReturn(Optional.empty());

        forumService.toggleUpvote(postId);
        assertThat(post.getUpvoteCount()).isEqualTo(1);
        verify(forumReactionRepository).save(any(ForumReactionEntity.class));

        forumService.toggleUpvote(postId);
        assertThat(post.getUpvoteCount()).isZero();
    }

    @Test
    void reportPostRejectsDuplicateReport() {
        UUID userId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();

        when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(principal(userId, UserRole.STUDENT)));
        when(forumPostRepository.findActiveById(postId)).thenReturn(Optional.of(post(postId, course(UUID.randomUUID(), UUID.randomUUID()), user(UUID.randomUUID(), UserRole.STUDENT))));
        when(forumReportRepository.findByTargetTypeAndTargetIdAndReporterId("POST", postId, userId))
                .thenReturn(Optional.of(new ForumReportEntity()));

        CreateForumReportRequest request = new CreateForumReportRequest();
        request.setReason("SPAM");

        assertThatThrownBy(() -> forumService.reportPost(postId, request))
                .isInstanceOf(BusinessException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.CONFLICT);
        verify(forumReportRepository, never()).save(any());
    }

    @Test
    void createReplyNotifiesPostAuthor() {
        UUID replierId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        ForumPostEntity post = post(postId, course(UUID.randomUUID(), UUID.randomUUID()), user(authorId, UserRole.STUDENT));
        post.setReplyCount(0);
        UserEntity replier = user(replierId, UserRole.STUDENT);

        when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(principal(replierId, UserRole.STUDENT)));
        when(forumPostRepository.findActiveById(postId)).thenReturn(Optional.of(post));
        when(userRepository.getReferenceById(replierId)).thenReturn(replier);
        when(forumReplyRepository.save(any(ForumReplyEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateForumReplyRequest request = new CreateForumReplyRequest();
        request.setContent("This is a helpful answer");

        forumService.createReply(postId, request);

        assertThat(post.getReplyCount()).isEqualTo(1);
        verify(notificationService).createNotification(eq(authorId), eq("FORUM_REPLY"), any(), any(), eq("FORUM_POST"), eq(postId), eq(replierId), any());
    }

    private CreateForumPostRequest createPostRequest(UUID courseId, boolean pinned) {
        CreateForumPostRequest request = new CreateForumPostRequest();
        request.setCourseId(courseId);
        request.setTopic("qa");
        request.setTitle("Question title");
        request.setContent("Question content");
        request.setPinned(pinned);
        return request;
    }

    private UserPrincipal principal(UUID userId, UserRole role) {
        return new UserPrincipal(user(userId, role));
    }

    private UserEntity user(UUID userId, UserRole role) {
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setEmail(userId + "@example.com");
        user.setPasswordHash("password");
        user.setFullName("Test User");
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }

    private ForumCourseEntity course(UUID courseId, UUID instructorId) {
        ForumCourseEntity course = new ForumCourseEntity();
        course.setId(courseId);
        course.setInstructorId(instructorId);
        course.setTitle("Course");
        return course;
    }

    private ForumPostEntity post(UUID postId, ForumCourseEntity course, UserEntity author) {
        ForumPostEntity post = new ForumPostEntity();
        post.setId(postId);
        post.setCourse(course);
        post.setAuthor(author);
        post.setTitle("Post title");
        post.setContent("Post content");
        post.setPinned(false);
        post.setReplyCount(0);
        post.setUpvoteCount(0);
        post.setDeleted(false);
        post.setCreatedAt(OffsetDateTime.now());
        post.setUpdatedAt(OffsetDateTime.now());
        return post;
    }
}
