package project.lms_rikkei_edu.modules.forum.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.transaction.annotation.Transactional;
import project.lms_rikkei_edu.common.exception.BusinessException;
import project.lms_rikkei_edu.common.security.CurrentUserProvider;
import project.lms_rikkei_edu.common.security.UserPrincipal;
import project.lms_rikkei_edu.modules.forum.dto.request.CreateForumPostRequest;
import project.lms_rikkei_edu.modules.forum.dto.request.CreateForumReplyRequest;
import project.lms_rikkei_edu.modules.forum.dto.request.UpdateForumPostRequest;
import project.lms_rikkei_edu.modules.forum.dto.response.ForumAuthorResponse;
import project.lms_rikkei_edu.modules.forum.dto.response.ForumCourseResponse;
import project.lms_rikkei_edu.modules.forum.dto.response.ForumPostDetailResponse;
import project.lms_rikkei_edu.modules.forum.dto.response.ForumPostResponse;
import project.lms_rikkei_edu.modules.forum.dto.response.ForumReplyResponse;
import project.lms_rikkei_edu.modules.forum.entity.ForumCourseEntity;
import project.lms_rikkei_edu.modules.forum.entity.ForumPostEntity;
import project.lms_rikkei_edu.modules.forum.entity.ForumReplyEntity;
import project.lms_rikkei_edu.modules.forum.repository.ForumCourseRepository;
import project.lms_rikkei_edu.modules.forum.repository.ForumPostRepository;
import project.lms_rikkei_edu.modules.forum.repository.ForumReplyRepository;
import project.lms_rikkei_edu.modules.forum.service.ForumService;
import project.lms_rikkei_edu.modules.user.entity.UserEntity;
import project.lms_rikkei_edu.modules.user.enums.UserRole;
import project.lms_rikkei_edu.modules.user.repository.UserRepository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RequiredArgsConstructor
public class ForumServiceImpl implements ForumService {

    private static final int MAX_REPLY_DEPTH = 3;

    private final ForumPostRepository forumPostRepository;
    private final ForumReplyRepository forumReplyRepository;
    private final ForumCourseRepository forumCourseRepository;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;

    @Override
    @Transactional(readOnly = true)
    public List<ForumCourseResponse> getCourses() {
        UserPrincipal currentUser = requireCurrentUser();

        List<ForumCourseEntity> courses;
        if (currentUser.getRole() == UserRole.ADMIN) {
            courses = forumCourseRepository.findAllForForum();
        } else if (currentUser.getRole() == UserRole.INSTRUCTOR) {
            courses = forumCourseRepository.findInstructorForumCourses(currentUser.getId());
        } else {
            courses = forumCourseRepository.findStudentForumCourses(currentUser.getId());
        }

        return courses.stream().map(this::toCourseResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ForumPostResponse> getPosts(UUID courseId, String keyword, Pageable pageable) {
        UserPrincipal currentUser = requireCurrentUser();
        String normalizedKeyword = normalizeKeyword(keyword);

        if (currentUser.getRole() == UserRole.ADMIN) {
            if (normalizedKeyword == null) {
                return forumPostRepository.findVisibleForAdmin(courseId, pageable).map(this::toPostResponse);
            }
            return forumPostRepository.searchVisibleForAdmin(courseId, normalizedKeyword, pageable).map(this::toPostResponse);
        }

        if (currentUser.getRole() == UserRole.INSTRUCTOR) {
            if (normalizedKeyword == null) {
                return forumPostRepository.findVisibleForInstructor(currentUser.getId(), courseId, pageable).map(this::toPostResponse);
            }
            return forumPostRepository.searchVisibleForInstructor(currentUser.getId(), courseId, normalizedKeyword, pageable).map(this::toPostResponse);
        }

        if (normalizedKeyword == null) {
            return forumPostRepository.findVisibleForStudent(currentUser.getId(), courseId, pageable).map(this::toPostResponse);
        }
        return forumPostRepository.searchVisibleForStudent(currentUser.getId(), courseId, normalizedKeyword, pageable).map(this::toPostResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ForumPostDetailResponse getPostDetail(UUID postId) {
        UserPrincipal currentUser = requireCurrentUser();
        ForumPostEntity post = findActivePost(postId);
        ensureCanAccessCourse(currentUser, post.getCourse().getId());

        List<ForumReplyResponse> replies = buildReplyTree(forumReplyRepository.findActiveByPostId(postId));

        return ForumPostDetailResponse.builder()
                .post(toPostResponse(post))
                .replies(replies)
                .build();
    }

    @Override
    @Transactional
    public ForumPostResponse createPost(CreateForumPostRequest request) {
        UserPrincipal currentUser = requireCurrentUser();
        ForumCourseEntity course = forumCourseRepository.findById(request.getCourseId())
                .orElseThrow(() -> new BusinessException("Course not found", HttpStatus.NOT_FOUND));

        ensureCanCreateInCourse(currentUser, course.getId(), Boolean.TRUE.equals(request.getPinned()));

        OffsetDateTime now = OffsetDateTime.now();
        ForumPostEntity post = new ForumPostEntity();
        post.setId(UUID.randomUUID());
        post.setCourse(course);
        post.setAuthor(userRepository.getReferenceById(currentUser.getId()));
        post.setTitle(request.getTitle().trim());
        post.setContent(request.getContent().trim());
        post.setPinned(Boolean.TRUE.equals(request.getPinned()) && currentUser.getRole() != UserRole.STUDENT);
        post.setReplyCount(0);
        post.setDeleted(false);
        post.setCreatedAt(now);
        post.setUpdatedAt(now);

        return toPostResponse(forumPostRepository.save(post));
    }

    @Override
    @Transactional
    public ForumReplyResponse createReply(UUID postId, CreateForumReplyRequest request) {
        UserPrincipal currentUser = requireCurrentUser();
        ForumPostEntity post = findActivePost(postId);
        ensureCanAccessCourse(currentUser, post.getCourse().getId());

        OffsetDateTime now = OffsetDateTime.now();
        ForumReplyEntity parentReply = resolveParentReply(post, request.getParentReplyId());
        ForumReplyEntity reply = new ForumReplyEntity();
        reply.setId(UUID.randomUUID());
        reply.setPost(post);
        reply.setCourse(post.getCourse());
        reply.setAuthor(userRepository.getReferenceById(currentUser.getId()));
        reply.setParentReply(parentReply);
        reply.setContent(request.getContent().trim());
        reply.setDeleted(false);
        reply.setCreatedAt(now);
        reply.setUpdatedAt(now);

        post.setReplyCount(Math.max(0, nullToZero(post.getReplyCount())) + 1);
        post.setUpdatedAt(now);

        return toReplyResponse(forumReplyRepository.save(reply), getReplyDepth(reply));
    }

    @Override
    @Transactional
    public ForumPostResponse updatePost(UUID postId, UpdateForumPostRequest request) {
        UserPrincipal currentUser = requireCurrentUser();
        ForumPostEntity post = findActivePost(postId);

        if (!isPostAuthor(currentUser, post)) {
            throw new BusinessException("You are not allowed to update this post", HttpStatus.FORBIDDEN);
        }

        if (Boolean.TRUE.equals(request.getPinned()) && currentUser.getRole() == UserRole.STUDENT) {
            throw new BusinessException("Students are not allowed to pin forum posts", HttpStatus.FORBIDDEN);
        }

        post.setTitle(request.getTitle().trim());
        post.setContent(request.getContent().trim());
        if (currentUser.getRole() == UserRole.ADMIN || currentUser.getRole() == UserRole.INSTRUCTOR) {
            post.setPinned(Boolean.TRUE.equals(request.getPinned()));
        }
        post.setUpdatedAt(OffsetDateTime.now());

        return toPostResponse(post);
    }

    @Override
    @Transactional
    public ForumReplyResponse updateReply(UUID replyId, CreateForumReplyRequest request) {
        UserPrincipal currentUser = requireCurrentUser();
        ForumReplyEntity reply = forumReplyRepository.findActiveById(replyId)
                .orElseThrow(() -> new BusinessException("Reply not found", HttpStatus.NOT_FOUND));

        if (!isReplyAuthor(currentUser, reply)) {
            throw new BusinessException("You are not allowed to update this reply", HttpStatus.FORBIDDEN);
        }

        reply.setContent(request.getContent().trim());
        reply.setUpdatedAt(OffsetDateTime.now());

        return toReplyResponse(reply, getReplyDepth(reply));
    }

    @Override
    @Transactional
    public void deletePost(UUID postId) {
        UserPrincipal currentUser = requireCurrentUser();
        ForumPostEntity post = findActivePost(postId);

        if (!isPostAuthor(currentUser, post)) {
            throw new BusinessException("You are not allowed to delete this post", HttpStatus.FORBIDDEN);
        }

        post.setDeleted(true);
        post.setDeletedBy(currentUser.getId());
        post.setDeletedAt(OffsetDateTime.now());
        post.setUpdatedAt(post.getDeletedAt());
    }

    @Override
    @Transactional
    public void deleteReply(UUID replyId) {
        UserPrincipal currentUser = requireCurrentUser();
        ForumReplyEntity reply = forumReplyRepository.findActiveById(replyId)
                .orElseThrow(() -> new BusinessException("Reply not found", HttpStatus.NOT_FOUND));

        if (!isReplyAuthor(currentUser, reply)) {
            throw new BusinessException("You are not allowed to delete this reply", HttpStatus.FORBIDDEN);
        }

        OffsetDateTime now = OffsetDateTime.now();
        reply.setDeleted(true);
        reply.setDeletedBy(currentUser.getId());
        reply.setDeletedAt(now);
        reply.setUpdatedAt(now);

        ForumPostEntity post = reply.getPost();
        post.setReplyCount(Math.max(0, nullToZero(post.getReplyCount()) - 1));
        post.setUpdatedAt(now);
    }

    private ForumPostEntity findActivePost(UUID postId) {
        return forumPostRepository.findActiveById(postId)
                .orElseThrow(() -> new BusinessException("Post not found", HttpStatus.NOT_FOUND));
    }

    private UserPrincipal requireCurrentUser() {
        return currentUserProvider.getCurrentUser()
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("User is not authenticated"));
    }

    private void ensureCanCreateInCourse(UserPrincipal currentUser, UUID courseId, boolean requestedPinned) {
        ensureCanAccessCourse(currentUser, courseId);

        if (requestedPinned && currentUser.getRole() == UserRole.STUDENT) {
            throw new BusinessException("Students are not allowed to pin forum posts", HttpStatus.FORBIDDEN);
        }
    }

    private void ensureCanAccessCourse(UserPrincipal currentUser, UUID courseId) {
        if (currentUser.getRole() == UserRole.ADMIN) {
            return;
        }

        if (currentUser.getRole() == UserRole.INSTRUCTOR
                && forumCourseRepository.isInstructorOfCourse(courseId, currentUser.getId())) {
            return;
        }

        if (currentUser.getRole() == UserRole.STUDENT
                && forumCourseRepository.isStudentEnrolled(courseId, currentUser.getId())) {
            return;
        }

        throw new BusinessException("You are not allowed to access this course forum", HttpStatus.FORBIDDEN);
    }

    private boolean isPostAuthor(UserPrincipal currentUser, ForumPostEntity post) {
        return post.getAuthor().getId().equals(currentUser.getId());
    }

    private boolean isReplyAuthor(UserPrincipal currentUser, ForumReplyEntity reply) {
        return reply.getAuthor().getId().equals(currentUser.getId());
    }

    private ForumPostResponse toPostResponse(ForumPostEntity post) {
        return ForumPostResponse.builder()
                .id(post.getId())
                .courseId(post.getCourse().getId())
                .courseTitle(post.getCourse().getTitle())
                .author(toAuthorResponse(post.getAuthor()))
                .title(post.getTitle())
                .content(post.getContent())
                .pinned(Boolean.TRUE.equals(post.getPinned()))
                .replyCount(nullToZero(post.getReplyCount()))
                .createdAt(post.getCreatedAt())
                .updatedAt(post.getUpdatedAt())
                .build();
    }

    private ForumCourseResponse toCourseResponse(ForumCourseEntity course) {
        return ForumCourseResponse.builder()
                .id(course.getId())
                .title(course.getTitle())
                .build();
    }

    private ForumReplyResponse toReplyResponse(ForumReplyEntity reply) {
        return toReplyResponse(reply, getReplyDepth(reply));
    }

    private ForumReplyResponse toReplyResponse(ForumReplyEntity reply, int depth) {
        return ForumReplyResponse.builder()
                .id(reply.getId())
                .postId(reply.getPost().getId())
                .courseId(reply.getCourse().getId())
                .parentReplyId(reply.getParentReply() == null ? null : reply.getParentReply().getId())
                .author(toAuthorResponse(reply.getAuthor()))
                .content(reply.getContent())
                .depth(depth)
                .replies(List.of())
                .createdAt(reply.getCreatedAt())
                .updatedAt(reply.getUpdatedAt())
                .build();
    }

    private ForumReplyEntity resolveParentReply(ForumPostEntity post, UUID parentReplyId) {
        if (parentReplyId == null) {
            return null;
        }

        ForumReplyEntity parentReply = forumReplyRepository.findActiveById(parentReplyId)
                .orElseThrow(() -> new BusinessException("Parent reply not found", HttpStatus.NOT_FOUND));

        if (!parentReply.getPost().getId().equals(post.getId())) {
            throw new BusinessException("Parent reply does not belong to this post");
        }

        if (getReplyDepth(parentReply) >= MAX_REPLY_DEPTH) {
            throw new BusinessException("Reply depth limit exceeded");
        }

        return parentReply;
    }

    private int getReplyDepth(ForumReplyEntity reply) {
        int depth = 1;
        ForumReplyEntity current = reply.getParentReply();
        while (current != null) {
            depth++;
            current = current.getParentReply();
        }
        return depth;
    }

    private List<ForumReplyResponse> buildReplyTree(List<ForumReplyEntity> replies) {
        Map<UUID, MutableForumReplyResponse> responseById = new LinkedHashMap<>();

        for (ForumReplyEntity reply : replies) {
            responseById.put(reply.getId(), new MutableForumReplyResponse(toReplyResponse(reply, getReplyDepth(reply))));
        }

        List<MutableForumReplyResponse> roots = new ArrayList<>();
        for (ForumReplyEntity reply : replies) {
            MutableForumReplyResponse current = responseById.get(reply.getId());
            UUID parentReplyId = reply.getParentReply() == null ? null : reply.getParentReply().getId();
            MutableForumReplyResponse parent = parentReplyId == null ? null : responseById.get(parentReplyId);
            if (parent == null) {
                roots.add(current);
            } else {
                parent.children.add(current);
            }
        }

        return roots.stream().map(MutableForumReplyResponse::toResponse).toList();
    }

    private static class MutableForumReplyResponse {
        private final ForumReplyResponse response;
        private final List<MutableForumReplyResponse> children = new ArrayList<>();

        private MutableForumReplyResponse(ForumReplyResponse response) {
            this.response = response;
        }

        private ForumReplyResponse toResponse() {
            return ForumReplyResponse.builder()
                    .id(response.getId())
                    .postId(response.getPostId())
                    .courseId(response.getCourseId())
                    .parentReplyId(response.getParentReplyId())
                    .author(response.getAuthor())
                    .content(response.getContent())
                    .depth(response.getDepth())
                    .replies(children.stream().map(MutableForumReplyResponse::toResponse).toList())
                    .createdAt(response.getCreatedAt())
                    .updatedAt(response.getUpdatedAt())
                    .build();
        }
    }

    private ForumAuthorResponse toAuthorResponse(UserEntity author) {
        return ForumAuthorResponse.builder()
                .id(author.getId())
                .fullName(author.getFullName())
                .role(author.getRole() == null ? null : author.getRole().name())
                .avatarUrl(author.getAvatarUrl())
                .build();
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim();
    }

    private int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }
}
