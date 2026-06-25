package project.lms_rikkei_edu.modules.forum.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
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
import project.lms_rikkei_edu.modules.forum.dto.request.UpdateForumPostRequest;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
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
    private ForumAttachmentService forumAttachmentService;
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
                forumAttachmentService,
                notificationService,
                userRepository,
                currentUserProvider
        );
        lenient().when(forumAttachmentService.findByPostIds(anyList())).thenReturn(Map.of());
        lenient().when(forumAttachmentService.findByReplyIds(anyList())).thenReturn(Map.of());
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
    void createPostPreservesForumAttachmentImageSource() {
        UUID studentId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();
        ForumCourseEntity course = course(courseId, UUID.randomUUID());

        when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(principal(studentId, UserRole.STUDENT)));
        when(forumCourseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(forumCourseRepository.isStudentEnrolled(courseId, studentId)).thenReturn(true);
        when(userRepository.getReferenceById(studentId)).thenReturn(user(studentId, UserRole.STUDENT));
        when(forumPostRepository.save(any(ForumPostEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateForumPostRequest request = createPostRequest(courseId, false);
        request.setContent("<figure><img src=\"http://localhost:8080/api/forum/attachments/" + attachmentId + "/content?token=old\"></figure>");

        forumService.createPost(request);

        ArgumentCaptor<ForumPostEntity> captor = ArgumentCaptor.forClass(ForumPostEntity.class);
        verify(forumPostRepository).save(captor.capture());
        assertThat(captor.getValue().getContent()).contains("src=\"/api/forum/attachments/" + attachmentId + "/content\"");
        assertThat(captor.getValue().getContent()).doesNotContain("token=old");
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
    void togglePinAllowsAdmin() {
        UUID adminId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        ForumPostEntity post = post(postId, course(UUID.randomUUID(), UUID.randomUUID()), user(UUID.randomUUID(), UserRole.STUDENT));

        when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(principal(adminId, UserRole.ADMIN)));
        when(forumPostRepository.findActiveById(postId)).thenReturn(Optional.of(post));
        when(forumReactionRepository.findByPostIdAndUserId(postId, adminId)).thenReturn(Optional.empty());

        var response = forumService.togglePin(postId);

        assertThat(response.isPinned()).isTrue();
        assertThat(post.getPinned()).isTrue();
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

    @Test
    void getCoursesReturnsCreateAndPinPermissions() {
        UUID adminId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        ForumCourseEntity course = course(courseId, UUID.randomUUID());

        when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(principal(adminId, UserRole.ADMIN)));
        when(forumCourseRepository.findAllForForum()).thenReturn(List.of(course));

        var courses = forumService.getCourses();

        assertThat(courses).hasSize(1);
        assertThat(courses.getFirst().isCanCreatePost()).isTrue();
        assertThat(courses.getFirst().isCanPinPost()).isTrue();
    }

    @Test
    void getCoursesCoversInstructorAndStudentPermissionBranches() {
        UUID instructorId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        ForumCourseEntity course = course(courseId, instructorId);

        when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(principal(instructorId, UserRole.INSTRUCTOR)));
        when(forumCourseRepository.findAllForForum()).thenReturn(List.of(course));
        when(forumCourseRepository.isInstructorOfCourse(courseId, instructorId)).thenReturn(true);

        var instructorCourses = forumService.getCourses();

        assertThat(instructorCourses.getFirst().isCanCreatePost()).isTrue();
        assertThat(instructorCourses.getFirst().isCanPinPost()).isTrue();

        UUID studentId = UUID.randomUUID();
        when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(principal(studentId, UserRole.STUDENT)));
        when(forumCourseRepository.findAllForForum()).thenReturn(List.of(course));
        when(forumCourseRepository.isStudentEnrolled(courseId, studentId)).thenReturn(true);

        var studentCourses = forumService.getCourses();

        assertThat(studentCourses.getFirst().isCanCreatePost()).isTrue();
        assertThat(studentCourses.getFirst().isCanPinPost()).isFalse();
    }

    @Test
    void getPostsUsesSearchAndMarksUpvotedPosts() {
        UUID userId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        ForumPostEntity post = post(postId, course(courseId, UUID.randomUUID()), user(UUID.randomUUID(), UserRole.STUDENT));

        when(forumPostRepository.searchActive(eq(courseId), eq("spring"), eq("qa"), any()))
                .thenReturn(new PageImpl<>(List.of(post), PageRequest.of(0, 10), 1));
        when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(principal(userId, UserRole.STUDENT)));
        when(forumReactionRepository.findPostIdsByPostIdInAndUserId(anyList(), eq(userId))).thenReturn(List.of(postId));

        var page = forumService.getPosts(courseId, " spring ", " qa ", PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().getFirst().isUpvoted()).isTrue();
    }

    @Test
    void getPostsUsesFindAllWhenKeywordIsBlankAndNoCurrentUser() {
        UUID courseId = UUID.randomUUID();
        ForumPostEntity post = post(UUID.randomUUID(), course(courseId, UUID.randomUUID()), user(UUID.randomUUID(), UserRole.STUDENT));

        when(forumPostRepository.findAllActive(eq(courseId), eq(null), any()))
                .thenReturn(new PageImpl<>(List.of(post), PageRequest.of(0, 10), 1));
        when(currentUserProvider.getCurrentUser()).thenReturn(Optional.empty());

        var page = forumService.getPosts(courseId, " ", null, PageRequest.of(0, 10));

        assertThat(page.getContent().getFirst().isUpvoted()).isFalse();
    }

    @Test
    void getPostDetailBuildsNestedRepliesAndMarksUpvotedReply() {
        UUID userId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();
        ForumCourseEntity course = course(UUID.randomUUID(), UUID.randomUUID());
        ForumPostEntity post = post(postId, course, user(UUID.randomUUID(), UserRole.STUDENT));
        ForumReplyEntity parent = reply(parentId, post, user(UUID.randomUUID(), UserRole.STUDENT), null);
        ForumReplyEntity child = reply(childId, post, user(UUID.randomUUID(), UserRole.STUDENT), parent);

        when(forumPostRepository.findActiveById(postId)).thenReturn(Optional.of(post));
        when(forumReplyRepository.findActiveByPostId(postId)).thenReturn(List.of(parent, child));
        when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(principal(userId, UserRole.STUDENT)));
        when(forumReactionRepository.findPostIdsByPostIdInAndUserId(anyList(), eq(userId))).thenReturn(List.of());
        when(forumReactionRepository.findReplyIdsByReplyIdInAndUserId(anyList(), eq(userId))).thenReturn(List.of(childId));

        var detail = forumService.getPostDetail(postId);

        assertThat(detail.getReplies()).hasSize(1);
        assertThat(detail.getReplies().getFirst().getReplies()).hasSize(1);
        assertThat(detail.getReplies().getFirst().getReplies().getFirst().isUpvoted()).isTrue();
        assertThat(detail.getReplies().getFirst().getReplies().getFirst().getDepth()).isEqualTo(2);
    }

    @Test
    void createPostAllowsInstructorToPinOwnCoursePost() {
        UUID instructorId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        ForumCourseEntity course = course(courseId, instructorId);

        when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(principal(instructorId, UserRole.INSTRUCTOR)));
        when(forumCourseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(forumCourseRepository.isInstructorOfCourse(courseId, instructorId)).thenReturn(true);
        when(userRepository.getReferenceById(instructorId)).thenReturn(user(instructorId, UserRole.INSTRUCTOR));
        when(forumPostRepository.save(any(ForumPostEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = forumService.createPost(createPostRequest(courseId, true));

        assertThat(response.isPinned()).isTrue();
    }

    @Test
    void createReplyNotifiesParentAuthorWithTrimmedLongContent() {
        UUID replierId = UUID.randomUUID();
        UUID postAuthorId = UUID.randomUUID();
        UUID parentAuthorId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        UUID parentReplyId = UUID.randomUUID();
        ForumPostEntity post = post(postId, course(UUID.randomUUID(), UUID.randomUUID()), user(postAuthorId, UserRole.STUDENT));
        ForumReplyEntity parentReply = reply(parentReplyId, post, user(parentAuthorId, UserRole.STUDENT), null);
        UserEntity replier = user(replierId, UserRole.STUDENT);
        replier.setFullName(null);

        when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(principal(replierId, UserRole.STUDENT)));
        when(forumPostRepository.findActiveById(postId)).thenReturn(Optional.of(post));
        when(forumReplyRepository.findActiveById(parentReplyId)).thenReturn(Optional.of(parentReply));
        when(userRepository.getReferenceById(replierId)).thenReturn(replier);
        when(forumReplyRepository.save(any(ForumReplyEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateForumReplyRequest request = new CreateForumReplyRequest();
        request.setParentReplyId(parentReplyId);
        request.setContent("x".repeat(120));

        forumService.createReply(postId, request);

        verify(notificationService).createNotification(eq(postAuthorId), eq("FORUM_REPLY"), any(), eq("x".repeat(100) + "..."), eq("FORUM_POST"), eq(postId), eq(replierId), eq("Người dùng"));
        verify(notificationService).createNotification(eq(parentAuthorId), eq("FORUM_REPLY"), any(), eq("x".repeat(100) + "..."), eq("FORUM_POST"), eq(postId), eq(replierId), eq("Người dùng"));
    }

    @Test
    void createReplyRejectsParentFromDifferentPost() {
        UUID replierId = UUID.randomUUID();
        UUID parentReplyId = UUID.randomUUID();
        ForumPostEntity post = post(UUID.randomUUID(), course(UUID.randomUUID(), UUID.randomUUID()), user(UUID.randomUUID(), UserRole.STUDENT));
        ForumPostEntity otherPost = post(UUID.randomUUID(), course(UUID.randomUUID(), UUID.randomUUID()), user(UUID.randomUUID(), UserRole.STUDENT));
        ForumReplyEntity parentReply = reply(parentReplyId, otherPost, user(UUID.randomUUID(), UserRole.STUDENT), null);

        when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(principal(replierId, UserRole.STUDENT)));
        when(forumPostRepository.findActiveById(post.getId())).thenReturn(Optional.of(post));
        when(forumReplyRepository.findActiveById(parentReplyId)).thenReturn(Optional.of(parentReply));

        CreateForumReplyRequest request = new CreateForumReplyRequest();
        request.setParentReplyId(parentReplyId);
        request.setContent("Reply");

        assertThatThrownBy(() -> forumService.createReply(post.getId(), request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Parent reply does not belong to this post");
    }

    @Test
    void updatePostAllowsAuthorAdminToPin() {
        UUID adminId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        ForumPostEntity post = post(postId, course(UUID.randomUUID(), UUID.randomUUID()), user(adminId, UserRole.ADMIN));

        when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(principal(adminId, UserRole.ADMIN)));
        when(forumPostRepository.findActiveById(postId)).thenReturn(Optional.of(post));
        when(forumReactionRepository.findByPostIdAndUserId(postId, adminId)).thenReturn(Optional.empty());

        UpdateForumPostRequest request = new UpdateForumPostRequest();
        request.setTopic("announcement");
        request.setTitle("Updated title");
        request.setContent("Updated content");
        request.setPinned(true);

        var response = forumService.updatePost(postId, request);

        assertThat(response.isPinned()).isTrue();
        assertThat(response.getTitle()).isEqualTo("Updated title");
    }

    @Test
    void updatePostRejectsNonAuthor() {
        UUID userId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        ForumPostEntity post = post(postId, course(UUID.randomUUID(), UUID.randomUUID()), user(UUID.randomUUID(), UserRole.STUDENT));

        when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(principal(userId, UserRole.STUDENT)));
        when(forumPostRepository.findActiveById(postId)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> forumService.updatePost(postId, new UpdateForumPostRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void updateReplyAllowsAuthorAndRejectsOtherUser() {
        UUID authorId = UUID.randomUUID();
        UUID replyId = UUID.randomUUID();
        ForumReplyEntity reply = reply(replyId, post(UUID.randomUUID(), course(UUID.randomUUID(), UUID.randomUUID()), user(UUID.randomUUID(), UserRole.STUDENT)), user(authorId, UserRole.STUDENT), null);

        when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(principal(authorId, UserRole.STUDENT)));
        when(forumReplyRepository.findActiveById(replyId)).thenReturn(Optional.of(reply));
        when(forumReactionRepository.findByReplyIdAndUserId(replyId, authorId)).thenReturn(Optional.empty());

        CreateForumReplyRequest request = new CreateForumReplyRequest();
        request.setContent("Updated reply");

        var response = forumService.updateReply(replyId, request);

        assertThat(response.getContent()).isEqualTo("Updated reply");

        UUID otherUserId = UUID.randomUUID();
        when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(principal(otherUserId, UserRole.STUDENT)));
        when(forumReplyRepository.findActiveById(replyId)).thenReturn(Optional.of(reply));

        assertThatThrownBy(() -> forumService.updateReply(replyId, request))
                .isInstanceOf(BusinessException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void deletePostMarksAuthorPostDeleted() {
        UUID authorId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        ForumPostEntity post = post(postId, course(UUID.randomUUID(), UUID.randomUUID()), user(authorId, UserRole.STUDENT));

        when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(principal(authorId, UserRole.STUDENT)));
        when(forumPostRepository.findActiveById(postId)).thenReturn(Optional.of(post));

        forumService.deletePost(postId);

        assertThat(post.getDeleted()).isTrue();
        assertThat(post.getDeletedBy()).isEqualTo(authorId);
    }

    @Test
    void deletePostRejectsNonAuthor() {
        UUID userId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        ForumPostEntity post = post(postId, course(UUID.randomUUID(), UUID.randomUUID()), user(UUID.randomUUID(), UserRole.STUDENT));

        when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(principal(userId, UserRole.STUDENT)));
        when(forumPostRepository.findActiveById(postId)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> forumService.deletePost(postId))
                .isInstanceOf(BusinessException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void deleteReplyMarksAuthorReplyDeletedAndDecrementsPostReplyCount() {
        UUID authorId = UUID.randomUUID();
        UUID replyId = UUID.randomUUID();
        ForumPostEntity post = post(UUID.randomUUID(), course(UUID.randomUUID(), UUID.randomUUID()), user(UUID.randomUUID(), UserRole.STUDENT));
        post.setReplyCount(2);
        ForumReplyEntity reply = reply(replyId, post, user(authorId, UserRole.STUDENT), null);

        when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(principal(authorId, UserRole.STUDENT)));
        when(forumReplyRepository.findActiveById(replyId)).thenReturn(Optional.of(reply));

        forumService.deleteReply(replyId);

        assertThat(reply.getDeleted()).isTrue();
        assertThat(reply.getDeletedBy()).isEqualTo(authorId);
        assertThat(post.getReplyCount()).isEqualTo(1);
    }

    @Test
    void deleteReplyRejectsNonAuthor() {
        UUID userId = UUID.randomUUID();
        UUID replyId = UUID.randomUUID();
        ForumReplyEntity reply = reply(replyId, post(UUID.randomUUID(), course(UUID.randomUUID(), UUID.randomUUID()), user(UUID.randomUUID(), UserRole.STUDENT)), user(UUID.randomUUID(), UserRole.STUDENT), null);

        when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(principal(userId, UserRole.STUDENT)));
        when(forumReplyRepository.findActiveById(replyId)).thenReturn(Optional.of(reply));

        assertThatThrownBy(() -> forumService.deleteReply(replyId))
                .isInstanceOf(BusinessException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void toggleReplyUpvoteAddsAndRemovesReaction() {
        UUID userId = UUID.randomUUID();
        UUID replyId = UUID.randomUUID();
        ForumReplyEntity reply = reply(replyId, post(UUID.randomUUID(), course(UUID.randomUUID(), UUID.randomUUID()), user(UUID.randomUUID(), UserRole.STUDENT)), user(UUID.randomUUID(), UserRole.STUDENT), null);
        reply.setUpvoteCount(0);

        when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(principal(userId, UserRole.STUDENT)));
        when(forumReplyRepository.findActiveById(replyId)).thenReturn(Optional.of(reply));
        when(forumReactionRepository.findByReplyIdAndUserId(replyId, userId))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(new ForumReactionEntity()))
                .thenReturn(Optional.of(new ForumReactionEntity()))
                .thenReturn(Optional.empty());

        forumService.toggleReplyUpvote(replyId);
        assertThat(reply.getUpvoteCount()).isEqualTo(1);
        verify(forumReactionRepository).save(any(ForumReactionEntity.class));

        forumService.toggleReplyUpvote(replyId);
        assertThat(reply.getUpvoteCount()).isZero();
    }

    @Test
    void reportPostSavesNewReport() {
        UUID userId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(principal(userId, UserRole.STUDENT)));
        when(forumPostRepository.findActiveById(postId)).thenReturn(Optional.of(post(postId, course(UUID.randomUUID(), UUID.randomUUID()), user(UUID.randomUUID(), UserRole.STUDENT))));
        when(forumReportRepository.findByTargetTypeAndTargetIdAndReporterId("POST", postId, userId)).thenReturn(Optional.empty());

        CreateForumReportRequest request = new CreateForumReportRequest();
        request.setReason("SPAM");
        request.setDescription("Bad content");

        forumService.reportPost(postId, request);

        ArgumentCaptor<ForumReportEntity> captor = ArgumentCaptor.forClass(ForumReportEntity.class);
        verify(forumReportRepository).save(captor.capture());
        assertThat(captor.getValue().getTargetType()).isEqualTo("POST");
        assertThat(captor.getValue().getReporterId()).isEqualTo(userId);
    }

    @Test
    void createPostWithAttachmentIdsCallsAttachToPost() {
        UUID studentId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();
        ForumCourseEntity course = course(courseId, UUID.randomUUID());
        UserEntity user = user(studentId, UserRole.STUDENT);

        when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(principal(studentId, UserRole.STUDENT)));
        when(forumCourseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(forumCourseRepository.isStudentEnrolled(courseId, studentId)).thenReturn(true);
        when(userRepository.getReferenceById(studentId)).thenReturn(user);
        when(forumPostRepository.save(any(ForumPostEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateForumPostRequest request = createPostRequest(courseId, false);
        request.setAttachmentIds(List.of(attachmentId));

        forumService.createPost(request);

        ArgumentCaptor<ForumPostEntity> captor = ArgumentCaptor.forClass(ForumPostEntity.class);
        verify(forumPostRepository).save(captor.capture());
        verify(forumAttachmentService).attachToPost(List.of(attachmentId), captor.getValue().getId(), studentId);
    }

    @Test
    void createReplyWithAttachmentIdsCallsAttachToReply() {
        UUID replierId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();
        ForumPostEntity post = post(postId, course(UUID.randomUUID(), UUID.randomUUID()), user(UUID.randomUUID(), UserRole.STUDENT));
        UserEntity replier = user(replierId, UserRole.STUDENT);

        when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(principal(replierId, UserRole.STUDENT)));
        when(forumPostRepository.findActiveById(postId)).thenReturn(Optional.of(post));
        when(userRepository.getReferenceById(replierId)).thenReturn(replier);
        when(forumReplyRepository.save(any(ForumReplyEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateForumReplyRequest request = new CreateForumReplyRequest();
        request.setContent("Reply with attachment");
        request.setAttachmentIds(List.of(attachmentId));

        forumService.createReply(postId, request);

        ArgumentCaptor<ForumReplyEntity> captor = ArgumentCaptor.forClass(ForumReplyEntity.class);
        verify(forumReplyRepository).save(captor.capture());
        verify(forumAttachmentService).attachToReply(List.of(attachmentId), captor.getValue().getId(), replierId);
    }

    @Test
    void updatePostWithAttachmentIdsCallsAttachToPost() {
        UUID authorId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();
        ForumPostEntity post = post(postId, course(UUID.randomUUID(), UUID.randomUUID()), user(authorId, UserRole.STUDENT));

        when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(principal(authorId, UserRole.STUDENT)));
        when(forumPostRepository.findActiveById(postId)).thenReturn(Optional.of(post));

        UpdateForumPostRequest request = new UpdateForumPostRequest();
        request.setTopic("announcement");
        request.setTitle("Updated");
        request.setContent("Updated content");
        request.setAttachmentIds(List.of(attachmentId));

        forumService.updatePost(postId, request);

        verify(forumAttachmentService).attachToPost(List.of(attachmentId), postId, authorId);
    }

    @Test
    void createPostSanitizesDangerousHtml() {
        UUID studentId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        ForumCourseEntity course = course(courseId, UUID.randomUUID());
        UserEntity user = user(studentId, UserRole.STUDENT);

        when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(principal(studentId, UserRole.STUDENT)));
        when(forumCourseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(forumCourseRepository.isStudentEnrolled(courseId, studentId)).thenReturn(true);
        when(userRepository.getReferenceById(studentId)).thenReturn(user);
        when(forumPostRepository.save(any(ForumPostEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String dangerousContent = "<p>Hello</p><script>alert('xss')</script><img src=\"javascript:alert(1)\">";
        CreateForumPostRequest request = createPostRequest(courseId, false);
        request.setContent(dangerousContent);

        forumService.createPost(request);

        ArgumentCaptor<ForumPostEntity> captor = ArgumentCaptor.forClass(ForumPostEntity.class);
        verify(forumPostRepository).save(captor.capture());
        String savedContent = captor.getValue().getContent();
        assertThat(savedContent).doesNotContain("script");
        assertThat(savedContent).doesNotContain("javascript:");
        assertThat(savedContent).contains("<p>Hello</p>");
    }

    @Test
    void updatePostSanitizesDangerousHtml() {
        UUID authorId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        ForumPostEntity post = post(postId, course(UUID.randomUUID(), UUID.randomUUID()), user(authorId, UserRole.STUDENT));

        when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(principal(authorId, UserRole.STUDENT)));
        when(forumPostRepository.findActiveById(postId)).thenReturn(Optional.of(post));

        String dangerousContent = "<b>Safe</b><iframe src=\"http://evil.com\"></iframe>";
        UpdateForumPostRequest request = new UpdateForumPostRequest();
        request.setTopic("qa");
        request.setTitle("Title");
        request.setContent(dangerousContent);

        forumService.updatePost(postId, request);

        assertThat(post.getContent()).doesNotContain("iframe");
        assertThat(post.getContent()).contains("<b>Safe</b>");
    }

    @Test
    void reportReplySavesNewReportAndRejectsDuplicate() {
        UUID userId = UUID.randomUUID();
        UUID replyId = UUID.randomUUID();
        ForumReplyEntity reply = reply(replyId, post(UUID.randomUUID(), course(UUID.randomUUID(), UUID.randomUUID()), user(UUID.randomUUID(), UserRole.STUDENT)), user(UUID.randomUUID(), UserRole.STUDENT), null);
        CreateForumReportRequest request = new CreateForumReportRequest();
        request.setReason("ABUSE");

        when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(principal(userId, UserRole.STUDENT)));
        when(forumReplyRepository.findActiveById(replyId)).thenReturn(Optional.of(reply));
        when(forumReportRepository.findByTargetTypeAndTargetIdAndReporterId("REPLY", replyId, userId))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(new ForumReportEntity()));

        forumService.reportReply(replyId, request);
        verify(forumReportRepository).save(any(ForumReportEntity.class));

        assertThatThrownBy(() -> forumService.reportReply(replyId, request))
                .isInstanceOf(BusinessException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.CONFLICT);
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

    private ForumReplyEntity reply(UUID replyId, ForumPostEntity post, UserEntity author, ForumReplyEntity parentReply) {
        ForumReplyEntity reply = new ForumReplyEntity();
        reply.setId(replyId);
        reply.setPost(post);
        reply.setCourse(post.getCourse());
        reply.setAuthor(author);
        reply.setParentReply(parentReply);
        reply.setContent("Reply content");
        reply.setUpvoteCount(0);
        reply.setDeleted(false);
        reply.setCreatedAt(OffsetDateTime.now());
        reply.setUpdatedAt(OffsetDateTime.now());
        return reply;
    }
}
