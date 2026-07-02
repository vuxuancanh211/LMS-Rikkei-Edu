package project.lms_rikkei_edu.modules.group.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.transaction.annotation.Transactional;
import project.lms_rikkei_edu.common.exception.BusinessException;
import project.lms_rikkei_edu.common.security.CurrentUserProvider;
import project.lms_rikkei_edu.common.security.UserPrincipal;
import project.lms_rikkei_edu.modules.course.entity.Course;
import project.lms_rikkei_edu.modules.course.repository.CourseRepository;
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
import project.lms_rikkei_edu.modules.user.entity.UserEntity;
import project.lms_rikkei_edu.modules.user.enums.UserRole;
import project.lms_rikkei_edu.modules.user.repository.UserRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@org.springframework.stereotype.Service
public class GroupServiceImpl implements GroupService {

    private final StudyGroupRepository studyGroupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;

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

        studyGroupRepository.save(group);
        return toGroupResponse(group);
    }

    @Override
    @Transactional
    public GroupResponse updateGroup(UUID groupId, UpdateGroupRequest request) {
        UserPrincipal currentUser = requireCurrentUser();
        StudyGroupEntity group = findGroupForInstructor(groupId, currentUser.getId());

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
        return toGroupResponse(group);
    }

    @Override
    @Transactional
    public void deleteGroup(UUID groupId) {
        UserPrincipal currentUser = requireCurrentUser();
        StudyGroupEntity group = findGroupForInstructor(groupId, currentUser.getId());
        groupMemberRepository.deleteByGroupId(groupId);
        studyGroupRepository.delete(group);
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
        return members.stream().map(this::toMemberResponse).toList();
    }

    @Override
    @Transactional
    public void removeMember(UUID groupId, UUID studentId) {
        UserPrincipal currentUser = requireCurrentUser();
        findGroupForInstructor(groupId, currentUser.getId());

        if (!groupMemberRepository.existsByGroupIdAndStudentId(groupId, studentId)) {
            throw new BusinessException("Student is not a member of this group", HttpStatus.NOT_FOUND);
        }

        groupMemberRepository.deleteByGroupIdAndStudentId(groupId, studentId);
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
