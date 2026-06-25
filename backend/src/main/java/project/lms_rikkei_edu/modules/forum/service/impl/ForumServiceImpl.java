package project.lms_rikkei_edu.modules.forum.service.impl;

import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
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
import project.lms_rikkei_edu.modules.forum.dto.response.ForumAttachmentResponse;
import project.lms_rikkei_edu.modules.forum.dto.response.ForumAuthorResponse;
import project.lms_rikkei_edu.modules.forum.dto.response.ForumCourseResponse;
import project.lms_rikkei_edu.modules.forum.dto.response.ForumPostDetailResponse;
import project.lms_rikkei_edu.modules.forum.dto.response.ForumPostResponse;
import project.lms_rikkei_edu.modules.forum.dto.response.ForumReplyResponse;
import project.lms_rikkei_edu.modules.forum.entity.ForumCourseEntity;
import project.lms_rikkei_edu.modules.forum.entity.ForumPostEntity;
import project.lms_rikkei_edu.modules.forum.entity.ForumReactionEntity;
import project.lms_rikkei_edu.modules.forum.entity.ForumReplyEntity;
import project.lms_rikkei_edu.modules.forum.repository.ForumCourseRepository;
import project.lms_rikkei_edu.modules.forum.repository.ForumPostRepository;
import project.lms_rikkei_edu.modules.forum.repository.ForumReactionRepository;
import project.lms_rikkei_edu.modules.forum.repository.ForumReportRepository;
import project.lms_rikkei_edu.modules.forum.repository.ForumReplyRepository;
import project.lms_rikkei_edu.modules.forum.dto.request.CreateForumReportRequest;
import project.lms_rikkei_edu.modules.forum.entity.ForumReportEntity;
import project.lms_rikkei_edu.modules.forum.service.ForumAttachmentService;
import project.lms_rikkei_edu.modules.forum.service.ForumService;
import project.lms_rikkei_edu.modules.notification.service.NotificationService;
import project.lms_rikkei_edu.modules.user.entity.UserEntity;
import project.lms_rikkei_edu.modules.user.enums.UserRole;
import project.lms_rikkei_edu.modules.user.repository.UserRepository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@RequiredArgsConstructor
public class ForumServiceImpl implements ForumService {

    private static final int MAX_REPLY_DEPTH = 3;
    private static final String ATTACHMENT_IMAGE_PLACEHOLDER_ORIGIN = "https://forum-attachment.local";
    private static final Pattern ATTACHMENT_IMAGE_SRC_PATTERN = Pattern.compile(
            "(?i)(src\\s*=\\s*['\\\"])(?:https?://[^/'\\\"]+)?(/api/forum/attachments/[0-9a-f-]{36}/content)(?:\\?[^'\\\"]*)?(['\\\"])"
    );
    private static final Safelist FORUM_CONTENT_SAFELIST = Safelist.relaxed()
            .addTags("pre", "code", "figure", "figcaption")
            .addAttributes("*", "class")
            .addAttributes("img", "src", "alt", "width", "height")
            .addAttributes("a", "href", "target", "rel")
            .addProtocols("img", "src", "http", "https")
            .addProtocols("a", "href", "http", "https", "mailto")
            .preserveRelativeLinks(true);

    private final ForumPostRepository forumPostRepository;
    private final ForumReplyRepository forumReplyRepository;
    private final ForumCourseRepository forumCourseRepository;
    private final ForumReactionRepository forumReactionRepository;
    private final ForumReportRepository forumReportRepository;
    private final ForumAttachmentService forumAttachmentService;
    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;

    @Override
    @Transactional(readOnly = true)
    public List<ForumCourseResponse> getCourses() {
        UserPrincipal currentUser = requireCurrentUser();
        return forumCourseRepository.findAllForForum()
                .stream().map(course -> toCourseResponse(course, currentUser)).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ForumPostResponse> getPosts(UUID courseId, String keyword, String topic, Pageable pageable) {
        String normalizedKeyword = normalizeKeyword(keyword);
        String normalizedTopic = normalizeKeyword(topic);

        Page<ForumPostEntity> postPage;
        if (normalizedKeyword == null) {
            postPage = forumPostRepository.findAllActive(courseId, normalizedTopic, pageable);
        } else {
            postPage = forumPostRepository.searchActive(courseId, normalizedKeyword, normalizedTopic, pageable);
        }

        Set<UUID> upvotedPostIds = resolveUpvotedPostIds(postPage.getContent());
        Map<UUID, List<ForumAttachmentResponse>> attachmentsByPostId = forumAttachmentService.findByPostIds(
                postPage.getContent().stream().map(ForumPostEntity::getId).toList()
        );
        return postPage.map(post -> toPostResponse(post, upvotedPostIds, attachmentsByPostId.getOrDefault(post.getId(), List.of())));
    }

    @Override
    @Transactional(readOnly = true)
    public ForumPostDetailResponse getPostDetail(UUID postId) {
        ForumPostEntity post = findActivePost(postId);
        List<ForumReplyEntity> replyEntities = forumReplyRepository.findActiveByPostId(postId);

        Set<UUID> upvotedPostIds = resolveUpvotedPostIds(List.of(post));
        Set<UUID> upvotedReplyIds = resolveUpvotedReplyIds(replyEntities);
        Map<UUID, List<ForumAttachmentResponse>> attachmentsByPostId = forumAttachmentService.findByPostIds(List.of(postId));
        Map<UUID, List<ForumAttachmentResponse>> attachmentsByReplyId = forumAttachmentService.findByReplyIds(
                replyEntities.stream().map(ForumReplyEntity::getId).toList()
        );

        List<ForumReplyResponse> replies = buildReplyTree(replyEntities, upvotedReplyIds, attachmentsByReplyId);

        return ForumPostDetailResponse.builder()
                .post(toPostResponse(post, upvotedPostIds, attachmentsByPostId.getOrDefault(postId, List.of())))
                .replies(replies)
                .build();
    }

    @Override
    @Transactional
    public ForumPostResponse createPost(CreateForumPostRequest request) {
        UserPrincipal currentUser = requireCurrentUser();
        ForumCourseEntity course = forumCourseRepository.findById(request.getCourseId())
                .orElseThrow(() -> new BusinessException("Course not found", HttpStatus.NOT_FOUND));

        ensureCanCreatePostInCourse(currentUser, course.getId());
        if (Boolean.TRUE.equals(request.getPinned())) {
            ensureCanPinInCourse(currentUser, course.getId());
        }

        OffsetDateTime now = OffsetDateTime.now();
        ForumPostEntity post = new ForumPostEntity();
        post.setId(UUID.randomUUID());
        post.setCourse(course);
        post.setAuthor(userRepository.getReferenceById(currentUser.getId()));
        post.setTopic(request.getTopic().trim());
        post.setTitle(request.getTitle().trim());
        post.setContent(sanitizeForumContent(request.getContent()));
        post.setPinned(Boolean.TRUE.equals(request.getPinned()));
        post.setReplyCount(0);
        post.setUpvoteCount(0);
        post.setDeleted(false);
        post.setCreatedAt(now);
        post.setUpdatedAt(now);

        ForumPostEntity savedPost = forumPostRepository.save(post);
        forumAttachmentService.attachToPost(request.getAttachmentIds(), savedPost.getId(), currentUser.getId());
        return toPostResponse(savedPost, Collections.emptySet(), forumAttachmentService.findByPostIds(List.of(savedPost.getId())).getOrDefault(savedPost.getId(), List.of()));
    }

    @Override
    @Transactional
    public ForumPostResponse togglePin(UUID postId) {
        UserPrincipal currentUser = requireCurrentUser();
        ForumPostEntity post = findActivePost(postId);
        ensureCanPinInCourse(currentUser, post.getCourse().getId());
        post.setPinned(!Boolean.TRUE.equals(post.getPinned()));
        post.setUpdatedAt(OffsetDateTime.now());
        return toPostResponse(post, findUpvotedPostIds(postId, currentUser.getId()), forumAttachmentService.findByPostIds(List.of(postId)).getOrDefault(postId, List.of()));
    }

    @Override
    @Transactional
    public ForumReplyResponse createReply(UUID postId, CreateForumReplyRequest request) {
        UserPrincipal currentUser = requireCurrentUser();
        ForumPostEntity post = findActivePost(postId);

        OffsetDateTime now = OffsetDateTime.now();
        ForumReplyEntity parentReply = resolveParentReply(post, request.getParentReplyId());
        ForumReplyEntity reply = new ForumReplyEntity();
        reply.setId(UUID.randomUUID());
        reply.setPost(post);
        reply.setCourse(post.getCourse());
        reply.setAuthor(userRepository.getReferenceById(currentUser.getId()));
        reply.setParentReply(parentReply);
        reply.setContent(sanitizeForumContent(request.getContent()));
        reply.setUpvoteCount(0);
        reply.setDeleted(false);
        reply.setCreatedAt(now);
        reply.setUpdatedAt(now);

        post.setReplyCount(Math.max(0, nullToZero(post.getReplyCount())) + 1);
        post.setUpdatedAt(now);

        ForumReplyEntity savedReplyEntity = forumReplyRepository.save(reply);
        forumAttachmentService.attachToReply(request.getAttachmentIds(), savedReplyEntity.getId(), currentUser.getId());
        ForumReplyResponse savedReply = toReplyResponse(savedReplyEntity, getReplyDepth(savedReplyEntity), Collections.emptySet(), forumAttachmentService.findByReplyIds(List.of(savedReplyEntity.getId())).getOrDefault(savedReplyEntity.getId(), List.of()));

        UUID currentUserId = currentUser.getId();
        String actorName = reply.getAuthor().getFullName() != null ? reply.getAuthor().getFullName() : "Người dùng";
        String notificationBody = summarizeForumContent(request.getContent());

        if (!post.getAuthor().getId().equals(currentUserId)) {
            notificationService.createNotification(
                    post.getAuthor().getId(),
                    "FORUM_REPLY",
                    actorName + " đã trả lời bài viết \"" + post.getTitle() + "\"",
                    notificationBody,
                    "FORUM_POST",
                    postId,
                    currentUserId,
                    actorName
            );
        }

        if (parentReply != null && !parentReply.getAuthor().getId().equals(currentUserId)
                && !parentReply.getAuthor().getId().equals(post.getAuthor().getId())) {
            notificationService.createNotification(
                    parentReply.getAuthor().getId(),
                    "FORUM_REPLY",
                    actorName + " đã trả lời bình luận của bạn trong \"" + post.getTitle() + "\"",
                    notificationBody,
                    "FORUM_POST",
                    postId,
                    currentUserId,
                    actorName
            );
        }

        return savedReply;
    }

    @Override
    @Transactional
    public ForumPostResponse updatePost(UUID postId, UpdateForumPostRequest request) {
        UserPrincipal currentUser = requireCurrentUser();
        ForumPostEntity post = findActivePost(postId);

        if (!isPostAuthor(currentUser, post)) {
            throw new BusinessException("You are not allowed to update this post", HttpStatus.FORBIDDEN);
        }

        if (Boolean.TRUE.equals(request.getPinned())) {
            ensureCanPinInCourse(currentUser, post.getCourse().getId());
        }

        post.setTopic(request.getTopic().trim());
        post.setTitle(request.getTitle().trim());
        post.setContent(sanitizeForumContent(request.getContent()));
        if (currentUser.getRole() == UserRole.ADMIN || currentUser.getRole() == UserRole.INSTRUCTOR) {
            post.setPinned(Boolean.TRUE.equals(request.getPinned()));
        }
        post.setUpdatedAt(OffsetDateTime.now());

        forumAttachmentService.attachToPost(request.getAttachmentIds(), post.getId(), currentUser.getId());
        return toPostResponse(post, findUpvotedPostIds(postId, currentUser.getId()), forumAttachmentService.findByPostIds(List.of(postId)).getOrDefault(postId, List.of()));
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

        reply.setContent(sanitizeForumContent(request.getContent()));
        reply.setUpdatedAt(OffsetDateTime.now());

        forumAttachmentService.attachToReply(request.getAttachmentIds(), reply.getId(), currentUser.getId());
        return toReplyResponse(reply, getReplyDepth(reply), findUpvotedReplyIds(replyId, currentUser.getId()), forumAttachmentService.findByReplyIds(List.of(replyId)).getOrDefault(replyId, List.of()));
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

    @Override
    @Transactional
    public ForumPostResponse toggleUpvote(UUID postId) {
        UserPrincipal currentUser = requireCurrentUser();
        ForumPostEntity post = findActivePost(postId);

        Optional<ForumReactionEntity> existing = forumReactionRepository.findByPostIdAndUserId(postId, currentUser.getId());
        if (existing.isPresent()) {
            forumReactionRepository.delete(existing.get());
            post.setUpvoteCount(Math.max(0, nullToZero(post.getUpvoteCount()) - 1));
        } else {
            ForumReactionEntity reaction = new ForumReactionEntity();
            reaction.setId(UUID.randomUUID());
            reaction.setPostId(postId);
            reaction.setUserId(currentUser.getId());
            reaction.setCreatedAt(OffsetDateTime.now());
            forumReactionRepository.save(reaction);
            post.setUpvoteCount(nullToZero(post.getUpvoteCount()) + 1);
        }
        post.setUpdatedAt(OffsetDateTime.now());

        return toPostResponse(post, findUpvotedPostIds(postId, currentUser.getId()), forumAttachmentService.findByPostIds(List.of(postId)).getOrDefault(postId, List.of()));
    }

    @Override
    @Transactional
    public ForumReplyResponse toggleReplyUpvote(UUID replyId) {
        UserPrincipal currentUser = requireCurrentUser();
        ForumReplyEntity reply = forumReplyRepository.findActiveById(replyId)
                .orElseThrow(() -> new BusinessException("Reply not found", HttpStatus.NOT_FOUND));

        Optional<ForumReactionEntity> existing = forumReactionRepository.findByReplyIdAndUserId(replyId, currentUser.getId());
        if (existing.isPresent()) {
            forumReactionRepository.delete(existing.get());
            reply.setUpvoteCount(Math.max(0, nullToZero(reply.getUpvoteCount()) - 1));
        } else {
            ForumReactionEntity reaction = new ForumReactionEntity();
            reaction.setId(UUID.randomUUID());
            reaction.setReplyId(replyId);
            reaction.setUserId(currentUser.getId());
            reaction.setCreatedAt(OffsetDateTime.now());
            forumReactionRepository.save(reaction);
            reply.setUpvoteCount(nullToZero(reply.getUpvoteCount()) + 1);
        }
        reply.setUpdatedAt(OffsetDateTime.now());

        return toReplyResponse(reply, getReplyDepth(reply), findUpvotedReplyIds(replyId, currentUser.getId()), forumAttachmentService.findByReplyIds(List.of(replyId)).getOrDefault(replyId, List.of()));
    }

    @Override
    @Transactional
    public void reportPost(UUID postId, CreateForumReportRequest request) {
        UserPrincipal currentUser = requireCurrentUser();
        findActivePost(postId);

        if (forumReportRepository.findByTargetTypeAndTargetIdAndReporterId("POST", postId, currentUser.getId()).isPresent()) {
            throw new BusinessException("You have already reported this post", HttpStatus.CONFLICT);
        }

        ForumReportEntity report = new ForumReportEntity();
        report.setId(UUID.randomUUID());
        report.setTargetType("POST");
        report.setTargetId(postId);
        report.setReporterId(currentUser.getId());
        report.setReason(request.getReason());
        report.setDescription(request.getDescription());
        report.setStatus("PENDING");
        report.setCreatedAt(OffsetDateTime.now());
        forumReportRepository.save(report);
    }

    @Override
    @Transactional
    public void reportReply(UUID replyId, CreateForumReportRequest request) {
        UserPrincipal currentUser = requireCurrentUser();
        forumReplyRepository.findActiveById(replyId)
                .orElseThrow(() -> new BusinessException("Reply not found", HttpStatus.NOT_FOUND));

        if (forumReportRepository.findByTargetTypeAndTargetIdAndReporterId("REPLY", replyId, currentUser.getId()).isPresent()) {
            throw new BusinessException("You have already reported this reply", HttpStatus.CONFLICT);
        }

        ForumReportEntity report = new ForumReportEntity();
        report.setId(UUID.randomUUID());
        report.setTargetType("REPLY");
        report.setTargetId(replyId);
        report.setReporterId(currentUser.getId());
        report.setReason(request.getReason());
        report.setDescription(request.getDescription());
        report.setStatus("PENDING");
        report.setCreatedAt(OffsetDateTime.now());
        forumReportRepository.save(report);
    }

    private ForumPostEntity findActivePost(UUID postId) {
        return forumPostRepository.findActiveById(postId)
                .orElseThrow(() -> new BusinessException("Post not found", HttpStatus.NOT_FOUND));
    }

    private UserPrincipal requireCurrentUser() {
        return currentUserProvider.getCurrentUser()
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("User is not authenticated"));
    }

    private boolean isPostAuthor(UserPrincipal currentUser, ForumPostEntity post) {
        return post.getAuthor().getId().equals(currentUser.getId());
    }

    private boolean isReplyAuthor(UserPrincipal currentUser, ForumReplyEntity reply) {
        return reply.getAuthor().getId().equals(currentUser.getId());
    }

    private void ensureCanCreatePostInCourse(UserPrincipal currentUser, UUID courseId) {
        if (canCreatePostInCourse(currentUser, courseId)) {
            return;
        }
        throw new BusinessException("You are not allowed to create posts in this course forum", HttpStatus.FORBIDDEN);
    }

    private void ensureCanPinInCourse(UserPrincipal currentUser, UUID courseId) {
        if (canPinInCourse(currentUser, courseId)) {
            return;
        }
        throw new BusinessException("You are not allowed to pin posts in this course forum", HttpStatus.FORBIDDEN);
    }

    private boolean canCreatePostInCourse(UserPrincipal currentUser, UUID courseId) {
        if (currentUser.getRole() == UserRole.ADMIN) {
            return true;
        }
        if (currentUser.getRole() == UserRole.INSTRUCTOR
                && forumCourseRepository.isInstructorOfCourse(courseId, currentUser.getId())) {
            return true;
        }
        return currentUser.getRole() == UserRole.STUDENT
                && forumCourseRepository.isStudentEnrolled(courseId, currentUser.getId());
    }

    private boolean canPinInCourse(UserPrincipal currentUser, UUID courseId) {
        if (currentUser.getRole() == UserRole.ADMIN) {
            return true;
        }
        return currentUser.getRole() == UserRole.INSTRUCTOR
                && forumCourseRepository.isInstructorOfCourse(courseId, currentUser.getId());
    }

    private Set<UUID> resolveUpvotedPostIds(List<ForumPostEntity> posts) {
        UserPrincipal currentUser = currentUserProvider.getCurrentUser().orElse(null);
        if (currentUser == null || posts.isEmpty()) return Collections.emptySet();
        List<UUID> postIds = posts.stream().map(ForumPostEntity::getId).toList();
        return new HashSet<>(forumReactionRepository.findPostIdsByPostIdInAndUserId(postIds, currentUser.getId()));
    }

    private Set<UUID> resolveUpvotedReplyIds(List<ForumReplyEntity> replies) {
        UserPrincipal currentUser = currentUserProvider.getCurrentUser().orElse(null);
        if (currentUser == null || replies.isEmpty()) return Collections.emptySet();
        List<UUID> replyIds = replies.stream().map(ForumReplyEntity::getId).toList();
        return new HashSet<>(forumReactionRepository.findReplyIdsByReplyIdInAndUserId(replyIds, currentUser.getId()));
    }

    private Set<UUID> findUpvotedPostIds(UUID postId, UUID userId) {
        return forumReactionRepository.findByPostIdAndUserId(postId, userId).isPresent()
                ? Set.of(postId) : Collections.emptySet();
    }

    private Set<UUID> findUpvotedReplyIds(UUID replyId, UUID userId) {
        return forumReactionRepository.findByReplyIdAndUserId(replyId, userId).isPresent()
                ? Set.of(replyId) : Collections.emptySet();
    }

    private ForumPostResponse toPostResponse(ForumPostEntity post, Set<UUID> upvotedPostIds, List<ForumAttachmentResponse> attachments) {
        return ForumPostResponse.builder()
                .id(post.getId())
                .courseId(post.getCourse().getId())
                .courseTitle(post.getCourse().getTitle())
                .author(toAuthorResponse(post.getAuthor()))
                .topic(post.getTopic())
                .title(post.getTitle())
                .content(post.getContent())
                .attachments(attachments)
                .pinned(Boolean.TRUE.equals(post.getPinned()))
                .replyCount(nullToZero(post.getReplyCount()))
                .upvoteCount(nullToZero(post.getUpvoteCount()))
                .upvoted(upvotedPostIds.contains(post.getId()))
                .createdAt(post.getCreatedAt())
                .updatedAt(post.getUpdatedAt())
                .build();
    }

    private ForumCourseResponse toCourseResponse(ForumCourseEntity course, UserPrincipal currentUser) {
        return ForumCourseResponse.builder()
                .id(course.getId())
                .title(course.getTitle())
                .canCreatePost(canCreatePostInCourse(currentUser, course.getId()))
                .canPinPost(canPinInCourse(currentUser, course.getId()))
                .build();
    }

    private ForumReplyResponse toReplyResponse(ForumReplyEntity reply, int depth, Set<UUID> upvotedReplyIds, List<ForumAttachmentResponse> attachments) {
        return ForumReplyResponse.builder()
                .id(reply.getId())
                .postId(reply.getPost().getId())
                .courseId(reply.getCourse().getId())
                .parentReplyId(reply.getParentReply() == null ? null : reply.getParentReply().getId())
                .author(toAuthorResponse(reply.getAuthor()))
                .content(reply.getContent())
                .attachments(attachments)
                .depth(depth)
                .upvoteCount(nullToZero(reply.getUpvoteCount()))
                .upvoted(upvotedReplyIds.contains(reply.getId()))
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

    private Map<UUID, Integer> computeDepths(List<ForumReplyEntity> replies) {
        Map<UUID, Integer> depths = new HashMap<>();
        Map<UUID, ForumReplyEntity> byId = new LinkedHashMap<>();
        for (ForumReplyEntity r : replies) {
            byId.put(r.getId(), r);
        }
        for (ForumReplyEntity r : replies) {
            computeDepth(r, byId, depths);
        }
        return depths;
    }

    private int computeDepth(ForumReplyEntity reply, Map<UUID, ForumReplyEntity> byId, Map<UUID, Integer> cache) {
        if (cache.containsKey(reply.getId())) return cache.get(reply.getId());
        if (reply.getParentReply() == null) {
            cache.put(reply.getId(), 1);
            return 1;
        }
        ForumReplyEntity parent = byId.get(reply.getParentReply().getId());
        if (parent == null) {
            cache.put(reply.getId(), 1);
            return 1;
        }
        int depth = computeDepth(parent, byId, cache) + 1;
        cache.put(reply.getId(), depth);
        return depth;
    }

    private List<ForumReplyResponse> buildReplyTree(List<ForumReplyEntity> replies, Set<UUID> upvotedReplyIds, Map<UUID, List<ForumAttachmentResponse>> attachmentsByReplyId) {
        Map<UUID, Integer> depths = computeDepths(replies);
        Map<UUID, MutableForumReplyResponse> responseById = new LinkedHashMap<>();

        for (ForumReplyEntity reply : replies) {
            responseById.put(reply.getId(), new MutableForumReplyResponse(toReplyResponse(reply, depths.get(reply.getId()), upvotedReplyIds, attachmentsByReplyId.getOrDefault(reply.getId(), List.of()))));
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
                    .attachments(response.getAttachments())
                    .depth(response.getDepth())
                    .upvoteCount(response.getUpvoteCount())
                    .upvoted(response.isUpvoted())
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

    private String sanitizeForumContent(String content) {
        if (content == null) {
            return null;
        }
        String normalizedContent = normalizeForumAttachmentImageSources(content.trim());
        String cleanedContent = Jsoup.clean(normalizedContent, FORUM_CONTENT_SAFELIST);
        return restoreForumAttachmentImageSources(cleanedContent);
    }

    private String normalizeForumAttachmentImageSources(String content) {
        return ATTACHMENT_IMAGE_SRC_PATTERN.matcher(content)
                .replaceAll("$1" + ATTACHMENT_IMAGE_PLACEHOLDER_ORIGIN + "$2$3");
    }

    private String restoreForumAttachmentImageSources(String content) {
        return content.replace(ATTACHMENT_IMAGE_PLACEHOLDER_ORIGIN + "/api/forum/attachments/", "/api/forum/attachments/");
    }

    private String summarizeForumContent(String content) {
        String plainText = sanitizeForumContent(content)
                .replaceAll("<[^>]*>", " ")
                .replaceAll("&nbsp;", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return plainText.length() > 100 ? plainText.substring(0, 100) + "..." : plainText;
    }

    private int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }
}
