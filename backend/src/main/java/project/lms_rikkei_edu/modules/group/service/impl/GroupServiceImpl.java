package project.lms_rikkei_edu.modules.group.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import project.lms_rikkei_edu.common.exception.BusinessException;
import project.lms_rikkei_edu.common.security.CurrentUserProvider;
import project.lms_rikkei_edu.common.security.UserPrincipal;
import project.lms_rikkei_edu.infrastructure.sse.SseEmitterRegistry;
import project.lms_rikkei_edu.modules.chat.entity.ChatRoomEntity;
import project.lms_rikkei_edu.modules.chat.entity.ChatRoomMemberEntity;
import project.lms_rikkei_edu.modules.chat.service.ChatRoomService;
import project.lms_rikkei_edu.modules.course.entity.Course;
import project.lms_rikkei_edu.modules.course.entity.CourseEnrollmentEntity;
import project.lms_rikkei_edu.modules.course.repository.CourseEnrollmentRepository;
import project.lms_rikkei_edu.modules.course.repository.CourseRepository;
import project.lms_rikkei_edu.modules.course.service.StudentCourseService;
import project.lms_rikkei_edu.modules.group.dto.request.AddGroupMembersRequest;
import project.lms_rikkei_edu.modules.group.dto.request.CreateGroupRequest;
import project.lms_rikkei_edu.modules.group.dto.request.UpdateGroupRequest;
import project.lms_rikkei_edu.modules.group.dto.response.GroupDetailResponse;
import project.lms_rikkei_edu.modules.group.dto.response.GroupMemberResponse;
import project.lms_rikkei_edu.modules.group.dto.response.GroupResponse;
import project.lms_rikkei_edu.modules.group.dto.response.StudentSearchResponse;
import project.lms_rikkei_edu.modules.group.entity.GroupMemberEntity;
import project.lms_rikkei_edu.modules.group.entity.StudyGroupEntity;
import project.lms_rikkei_edu.modules.group.repository.GroupMemberRepository;
import project.lms_rikkei_edu.modules.group.repository.StudyGroupRepository;
import project.lms_rikkei_edu.modules.group.service.GroupService;
import project.lms_rikkei_edu.modules.notification.enums.NotificationType;
import project.lms_rikkei_edu.modules.notification.service.NotificationPreferenceService;
import project.lms_rikkei_edu.modules.notification.service.NotificationService;
import project.lms_rikkei_edu.modules.user.entity.UserEntity;
import project.lms_rikkei_edu.modules.user.enums.UserRole;
import project.lms_rikkei_edu.modules.user.repository.UserRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class GroupServiceImpl implements GroupService {

    private static final String GROUP_REFERENCE_TYPE = "GROUP";
    private static final String COURSE_TITLE_SEPARATOR = "\" của khóa học \"";

    private final StudyGroupRepository studyGroupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final CourseRepository courseRepository;
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final StudentCourseService studentCourseService;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;
    private final NotificationService notificationService;
    private final NotificationPreferenceService notificationPreferenceService;
    private final ChatRoomService chatRoomService;
    private final SseEmitterRegistry sseEmitterRegistry;

    @Override
    @Transactional(readOnly = true)
    public Page<GroupResponse> getGroups(UUID courseId, String keyword, Pageable pageable) {
        UserPrincipal currentUser = requireCurrentUser();
        String normalizedKeyword = keyword != null && !keyword.isBlank() ? keyword.trim() : null;

        Page<StudyGroupEntity> groupPage = studyGroupRepository.findByFilters(
                currentUser.getId(), courseId, normalizedKeyword, pageable);

        return groupPage.map(this::toGroupResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public GroupDetailResponse getGroupDetail(UUID groupId) {
        UserPrincipal currentUser = requireCurrentUser();
        StudyGroupEntity group = findGroupForInstructor(groupId, currentUser.getId());
        List<GroupMemberEntity> members = groupMemberRepository.findByGroupIdWithStudent(groupId);

        return toGroupDetailResponse(group, members);
    }

    @Override
    @Transactional
    public GroupResponse createGroup(CreateGroupRequest request) {
        UserPrincipal currentUser = requireCurrentUser();

        Course course = courseRepository.findById(request.getCourseId())
                .orElseThrow(() -> new BusinessException("Course not found", HttpStatus.NOT_FOUND));

        if (!course.getInstructorId().equals(currentUser.getId())
                && currentUser.getRole().name().equals("INSTRUCTOR")) {
            throw new BusinessException("You can only create groups for your own courses", HttpStatus.FORBIDDEN);
        }

        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        if (request.getStartDate().isBefore(today)) {
            throw new BusinessException("Start date must be today or later");
        }

        if (request.getEndDate() != null && request.getEndDate().isBefore(request.getStartDate())) {
            throw new BusinessException("End date must be after start date");
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneId.systemDefault());
        StudyGroupEntity group = new StudyGroupEntity();
        group.setId(UUID.randomUUID());
        group.setCourse(course);
        group.setInstructor(userRepository.getReferenceById(currentUser.getId()));
        group.setName(request.getName().trim());
        group.setDescription(request.getDescription());
        group.setMaxCapacity(request.getMaxCapacity());
        group.setStartDate(request.getStartDate());
        group.setEndDate(request.getEndDate());
        group.setCreatedAt(now);
        group.setUpdatedAt(now);

        group = studyGroupRepository.save(group);
        chatRoomService.getOrCreateRoomForGroup(group, group.getInstructor());
        return toGroupResponse(group);
    }

    @Override
    @Transactional
    public GroupResponse updateGroup(UUID groupId, UpdateGroupRequest request) {
        UserPrincipal currentUser = requireCurrentUser();
        StudyGroupEntity group = findGroupForInstructor(groupId, currentUser.getId());
        LocalDate oldStartDate = group.getStartDate();
        LocalDate oldEndDate = group.getEndDate();

        if (request.getEndDate() != null && request.getStartDate() != null
                && request.getEndDate().isBefore(request.getStartDate())) {
            throw new BusinessException("End date must be after start date");
        }

        if (request.getMaxCapacity() != null) {
            long currentCount = groupMemberRepository.countByGroupId(groupId);
            if (request.getMaxCapacity() < currentCount) {
                throw new BusinessException("Max capacity cannot be less than current member count of " + currentCount);
            }
        }

        group.setName(request.getName().trim());
        group.setDescription(request.getDescription());
        group.setMaxCapacity(request.getMaxCapacity());
        if (request.getStartDate() != null) {
            group.setStartDate(request.getStartDate());
        }
        group.setEndDate(request.getEndDate());
        group.setUpdatedAt(OffsetDateTime.now(ZoneId.systemDefault()));

        studyGroupRepository.save(group);
        if (!Objects.equals(oldStartDate, group.getStartDate()) || !Objects.equals(oldEndDate, group.getEndDate())) {
            notifyScheduleChanged(
                    group,
                    groupMemberRepository.findByGroupIdWithStudent(groupId),
                    currentUser,
                    oldStartDate,
                    oldEndDate);
        }
        return toGroupResponse(group);
    }

    @Override
    @Transactional
    public void deleteGroup(UUID groupId) {
        UserPrincipal currentUser = requireCurrentUser();
        StudyGroupEntity group = findGroupForInstructor(groupId, currentUser.getId());
        List<GroupMemberEntity> members = groupMemberRepository.findByGroupIdWithStudent(groupId);
        List<UUID> memberIds = members.stream().map(member -> member.getStudent().getId()).toList();
        notifyGroupDeleted(group, members, currentUser);
        groupMemberRepository.deleteByGroupId(groupId);
        chatRoomService.deleteRoomForGroup(groupId);
        studyGroupRepository.delete(group);
        sendChatRoomsChangedAfterCommit(memberIds, "GROUP_DELETED", groupId);
    }

    @Override
    @Transactional
    public List<GroupMemberResponse> addMembers(UUID groupId, AddGroupMembersRequest request) {
        UserPrincipal currentUser = requireCurrentUser();
        StudyGroupEntity group = findGroupForInstructor(groupId, currentUser.getId());

        List<String> emails = request.getEmails().stream()
                .map(String::trim)
                .filter(e -> !e.isEmpty())
                .distinct()
                .toList();

        if (emails.isEmpty()) {
            throw new BusinessException("Email list is empty");
        }

        List<UserEntity> students = userRepository.findByEmailIgnoreCaseInAndDeletedAtIsNull(emails);
        if (students.size() != emails.size()) {
            List<String> found = students.stream().map(u -> u.getEmail().toLowerCase()).toList();
            String missing = emails.stream()
                    .filter(e -> !found.contains(e.toLowerCase()))
                    .findFirst().orElse("");
            throw new BusinessException("Student not found with email: " + missing, HttpStatus.NOT_FOUND);
        }

        long currentCount = groupMemberRepository.countByGroupId(groupId);
        int maxCapacity = group.getMaxCapacity() != null ? group.getMaxCapacity() : Integer.MAX_VALUE;

        if (currentCount + students.size() > maxCapacity) {
            throw new BusinessException("Adding members would exceed group capacity of " + maxCapacity);
        }

        List<UUID> studentIds = students.stream().map(UserEntity::getId).toList();
        List<UUID> existing = groupMemberRepository.findExistingStudentIds(groupId, studentIds);
        if (!existing.isEmpty()) {
            UserEntity student = userRepository.findById(existing.get(0)).orElseThrow();
            throw new BusinessException("Student " + student.getFullName() + " is already a member of this group", HttpStatus.CONFLICT);
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneId.systemDefault());
        List<GroupMemberEntity> members = students.stream()
                .map(s -> {
                    GroupMemberEntity member = new GroupMemberEntity();
                    member.setId(UUID.randomUUID());
                    member.setGroup(group);
                    member.setStudent(s);
                    member.setJoinedAt(now);
                    return member;
                })
                .toList();

        groupMemberRepository.saveAll(members);
        enrollInCourse(group.getCourse().getId(), studentIds, currentUser.getId());
        ChatRoomEntity room = chatRoomService.getOrCreateRoomForGroup(group, group.getInstructor());
        students.forEach(student -> chatRoomService.addMember(
                room.getId(),
                student,
                ChatRoomMemberEntity.MemberRole.MEMBER));
        notifyAddedMembers(group, members, currentUser);
        sendChatRoomsChangedAfterCommit(studentIds, "GROUP_MEMBER_ADDED", groupId);
        return members.stream().map(this::toMemberResponse).toList();
    }

    /**
     * Thêm học viên vào nhóm không tự động cấp quyền truy cập khóa học — course_enrollments
     * là bảng duy nhất được StudentCourseServiceImpl dùng để kiểm tra "đã đăng ký khóa học chưa".
     * Tạo enrollment còn thiếu ở đây để học viên thấy khóa học ngay sau khi được thêm vào nhóm.
     */
    private void enrollInCourse(UUID courseId, List<UUID> studentIds, UUID enrolledBy) {
        List<UUID> alreadyEnrolled = courseEnrollmentRepository.findEnrolledStudentIds(courseId, studentIds);
        Instant now = Instant.now();
        List<CourseEnrollmentEntity> toCreate = studentIds.stream()
                .filter(id -> !alreadyEnrolled.contains(id))
                .map(id -> {
                    CourseEnrollmentEntity enrollment = new CourseEnrollmentEntity();
                    enrollment.setId(UUID.randomUUID());
                    enrollment.setCourseId(courseId);
                    enrollment.setStudentId(id);
                    enrollment.setEnrolledBy(enrolledBy);
                    enrollment.setEnrolledAt(now);
                    return enrollment;
                })
                .toList();
        if (!toCreate.isEmpty()) {
            courseEnrollmentRepository.saveAll(toCreate);
        }
        if (studentIds != null && !studentIds.isEmpty()) {
            studentCourseService.resetProgressForStudents(courseId, studentIds);
        }
    }

    private void notifyAddedMembers(StudyGroupEntity group, List<GroupMemberEntity> members, UserPrincipal actor) {
        String title = "Bạn đã được thêm vào nhóm học";
        String body = "Bạn đã được thêm vào nhóm \"" + group.getName()
                + COURSE_TITLE_SEPARATOR + group.getCourse().getTitle() + "\".";
        notifyMembers(group, members, NotificationType.GROUP_MEMBER_ADDED, title, body,
                "group-member-added", actor);
    }

    private void notifyScheduleChanged(StudyGroupEntity group, List<GroupMemberEntity> members, UserPrincipal actor,
                                       LocalDate oldStartDate, LocalDate oldEndDate) {
        String title = "Lịch nhóm học đã thay đổi";
        String body = "Lịch nhóm \"" + group.getName() + "\" đã thay đổi: "
                + formatRange(oldStartDate, oldEndDate) + " -> "
                + formatRange(group.getStartDate(), group.getEndDate()) + ".";
        notifyMembers(group, members, NotificationType.GROUP_SCHEDULE_CHANGED, title, body,
                "group-schedule-changed:" + group.getUpdatedAt().toInstant().toEpochMilli(), actor);
    }

    private void notifyGroupDeleted(StudyGroupEntity group, List<GroupMemberEntity> members, UserPrincipal actor) {
        String title = "Nhóm học đã bị xoá";
        String body = "Nhóm \"" + group.getName() + COURSE_TITLE_SEPARATOR
                + group.getCourse().getTitle() + "\" đã bị xoá.";
        notifyMembers(group, members, NotificationType.GROUP_DELETED, title, body,
                "group-deleted", actor);
    }

    @Override
    @Transactional
    public void removeMember(UUID groupId, UUID studentId) {
        UserPrincipal currentUser = requireCurrentUser();
        StudyGroupEntity group = findGroupForInstructor(groupId, currentUser.getId());

        GroupMemberEntity member = groupMemberRepository.findByGroupIdAndStudentIdWithStudent(groupId, studentId)
                .orElseThrow(() -> new BusinessException("Student is not a member of this group", HttpStatus.NOT_FOUND));

        notifyMemberRemoved(group, member, currentUser);
        groupMemberRepository.deleteByGroupIdAndStudentId(groupId, studentId);
        ChatRoomEntity room = chatRoomService.getOrCreateRoomForGroup(group, group.getInstructor());
        chatRoomService.removeMember(room.getId(), studentId);
        sendChatRoomsChangedAfterCommit(List.of(studentId), "GROUP_MEMBER_REMOVED", groupId);
    }

    private void sendChatRoomsChangedAfterCommit(List<UUID> userIds, String reason, UUID groupId) {
        Runnable send = () -> sseEmitterRegistry.sendToUsers(userIds, "CHAT_ROOMS_CHANGED", Map.of(
                "reason", reason,
                "groupId", groupId
        ));

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
            return;
        }

        send.run();
    }

    private void notifyMemberRemoved(StudyGroupEntity group, GroupMemberEntity member, UserPrincipal actor) {
        String title = "Bạn đã được xoá khỏi nhóm học";
        String body = "Bạn đã được xoá khỏi nhóm \"" + group.getName()
                + COURSE_TITLE_SEPARATOR + group.getCourse().getTitle() + "\".";
        notifyStudent(group, member.getStudent().getId(), NotificationType.GROUP_MEMBER_REMOVED, title, body,
                "group-member-removed:" + group.getId() + ":" + member.getStudent().getId(), actor);
    }

    @Override
    @Transactional(readOnly = true)
    public List<GroupResponse> getStudentGroups() {
        UserPrincipal currentUser = requireCurrentUser();
        return studyGroupRepository.findByStudentId(currentUser.getId())
                .stream()
                .map(this::toGroupResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public GroupDetailResponse getStudentGroupDetail(UUID groupId) {
        UserPrincipal currentUser = requireCurrentUser();
        StudyGroupEntity group = studyGroupRepository.findByIdWithCourse(groupId)
                .orElseThrow(() -> new BusinessException("Group not found", HttpStatus.NOT_FOUND));

        if (!groupMemberRepository.existsByGroupIdAndStudentId(groupId, currentUser.getId())) {
            throw new BusinessException("You are not a member of this group", HttpStatus.FORBIDDEN);
        }

        List<GroupMemberEntity> members = groupMemberRepository.findByGroupIdWithStudent(groupId);
        return toGroupDetailResponse(group, members);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudentSearchResponse> searchStudentsByEmail(String email) {
        if (email == null || email.isBlank()) {
            return List.of();
        }
        List<UserEntity> students = userRepository
                .searchStudentsByEmail(email.trim(), UserRole.STUDENT);
        return students.stream()
                .map(u -> StudentSearchResponse.builder()
                        .id(u.getId())
                        .email(u.getEmail())
                        .fullName(u.getFullName())
                        .avatarUrl(u.getAvatarUrl())
                        .build())
                .toList();
    }

    private StudyGroupEntity findGroupForInstructor(UUID groupId, UUID instructorId) {
        return studyGroupRepository.findByIdAndInstructorId(groupId, instructorId)
                .orElseThrow(() -> new BusinessException("Group not found", HttpStatus.NOT_FOUND));
    }

    private UserPrincipal requireCurrentUser() {
        return currentUserProvider.getCurrentUser()
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("User is not authenticated"));
    }

    private String computeGroupStatus(StudyGroupEntity group) {
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        if (group.getEndDate() != null && group.getEndDate().isBefore(today)) {
            return "COMPLETED";
        }
        if (group.getStartDate().isAfter(today)) {
            return "UPCOMING";
        }
        return "ACTIVE";
    }

    private void notifyMembers(StudyGroupEntity group, List<GroupMemberEntity> members, NotificationType type,
                               String title, String body, String keyPrefix, UserPrincipal actor) {
        for (GroupMemberEntity member : members) {
            UUID studentId = member.getStudent().getId();
            notifyStudent(group, studentId, type, title, body, notificationKey(keyPrefix, group.getId(), studentId), actor);
        }
    }

    private void notifyStudent(StudyGroupEntity group, UUID studentId, NotificationType type,
                               String title, String body, String idempotencyKey, UserPrincipal actor) {
        if (!notificationPreferenceService.isInAppEnabled(studentId, type.name())) {
            return;
        }
        notificationService.createNotification(
                studentId,
                type.name(),
                title,
                body,
                GROUP_REFERENCE_TYPE,
                group.getId(),
                actor.getId(),
                actor.getUsername(),
                idempotencyKey
        );
    }

    private String formatRange(LocalDate startDate, LocalDate endDate) {
        return startDate + (endDate != null ? " - " + endDate : "");
    }

    private String notificationKey(String prefix, UUID groupId, UUID studentId) {
        return prefix + ":" + groupId + ":" + studentId;
    }

    private GroupResponse toGroupResponse(StudyGroupEntity group) {
        return GroupResponse.builder()
                .id(group.getId())
                .courseId(group.getCourse().getId())
                .courseTitle(group.getCourse().getTitle())
                .name(group.getName())
                .description(group.getDescription())
                .maxCapacity(group.getMaxCapacity())
                .memberCount((int) groupMemberRepository.countByGroupId(group.getId()))
                .startDate(group.getStartDate())
                .endDate(group.getEndDate())
                .status(computeGroupStatus(group))
                .createdAt(group.getCreatedAt())
                .build();
    }

    private GroupDetailResponse toGroupDetailResponse(StudyGroupEntity group, List<GroupMemberEntity> members) {
        return GroupDetailResponse.builder()
                .id(group.getId())
                .courseId(group.getCourse().getId())
                .courseTitle(group.getCourse().getTitle())
                .name(group.getName())
                .description(group.getDescription())
                .maxCapacity(group.getMaxCapacity())
                .memberCount(members.size())
                .startDate(group.getStartDate())
                .endDate(group.getEndDate())
                .status(computeGroupStatus(group))
                .createdAt(group.getCreatedAt())
                .members(members.stream().map(this::toMemberResponse).toList())
                .build();
    }

    private GroupMemberResponse toMemberResponse(GroupMemberEntity member) {
        return GroupMemberResponse.builder()
                .id(member.getId())
                .studentId(member.getStudent().getId())
                .studentName(member.getStudent().getFullName())
                .studentEmail(member.getStudent().getEmail())
                .avatarUrl(member.getStudent().getAvatarUrl())
                .joinedAt(member.getJoinedAt())
                .build();
    }
}
