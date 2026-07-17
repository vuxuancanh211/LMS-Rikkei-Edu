package project.lms_rikkei_edu.modules.group.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import project.lms_rikkei_edu.common.exception.BusinessException;
import project.lms_rikkei_edu.common.security.CurrentUserProvider;
import project.lms_rikkei_edu.common.security.UserPrincipal;
import project.lms_rikkei_edu.infrastructure.sse.SseEmitterRegistry;
import project.lms_rikkei_edu.modules.chat.entity.ChatRoomEntity;
import project.lms_rikkei_edu.modules.chat.entity.ChatRoomMemberEntity;
import project.lms_rikkei_edu.modules.chat.service.ChatRoomService;
import project.lms_rikkei_edu.modules.course.entity.Course;
import project.lms_rikkei_edu.modules.course.repository.CourseEnrollmentRepository;
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
import project.lms_rikkei_edu.modules.notification.enums.NotificationType;
import project.lms_rikkei_edu.modules.notification.service.NotificationPreferenceService;
import project.lms_rikkei_edu.modules.notification.service.NotificationService;
import project.lms_rikkei_edu.modules.user.entity.UserEntity;
import project.lms_rikkei_edu.modules.user.enums.UserRole;
import project.lms_rikkei_edu.modules.user.enums.UserStatus;
import project.lms_rikkei_edu.modules.user.repository.UserRepository;

import jakarta.persistence.criteria.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupServiceImplTest {

    @Mock
    private StudyGroupRepository studyGroupRepository;
    @Mock
    private GroupMemberRepository groupMemberRepository;

    @Mock
    private CourseRepository courseRepository;
    @Mock
    private CourseEnrollmentRepository courseEnrollmentRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private NotificationService notificationService;
    @Mock
    private NotificationPreferenceService notificationPreferenceService;
    @Mock
    private ChatRoomService chatRoomService;
    @Mock
    private SseEmitterRegistry sseEmitterRegistry;
    @Mock
    private project.lms_rikkei_edu.modules.course.service.StudentCourseService studentCourseService;

    private GroupServiceImpl groupService;

    private final UUID instructorId = UUID.randomUUID();
    private final UUID otherInstructorId = UUID.randomUUID();
    private final UUID courseId = UUID.randomUUID();
    private final UUID groupId = UUID.randomUUID();
    private final UUID studentId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        groupService = new GroupServiceImpl(
                studyGroupRepository,
                groupMemberRepository,
                courseEnrollmentRepository,
                courseRepository,
                studentCourseService,
                userRepository,
                currentUserProvider,
                notificationService,
                notificationPreferenceService,
                chatRoomService,
                sseEmitterRegistry
        );
    }

    @Test
    void getGroups_returnsPagedGroupsForCurrentInstructor() {
        StudyGroupEntity group = groupEntity(groupId, courseEntity(instructorId), instructorUser());
        when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(principal(instructorId, UserRole.INSTRUCTOR)));
        when(studyGroupRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(group), PageRequest.of(0, 10), 1));
        when(groupMemberRepository.countByGroupIds(any())).thenReturn(Collections.singletonList(new Object[]{groupId, 2L}));

        var result = groupService.getGroups(courseId, " React ", PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().getName()).isEqualTo("Group A");
        assertThat(result.getContent().getFirst().getMemberCount()).isEqualTo(2);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void getGroups_specificationBranchesCovered() {
        when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(principal(instructorId, UserRole.INSTRUCTOR)));
        ArgumentCaptor<Specification<StudyGroupEntity>> captor = ArgumentCaptor.forClass(Specification.class);
        when(studyGroupRepository.findAll(captor.capture(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 10), 0));

        // 1. Call getGroups with courseId and keyword "Java React"
        groupService.getGroups(courseId, "Java React", PageRequest.of(0, 10));
        Specification<StudyGroupEntity> spec1 = captor.getValue();

        Root<StudyGroupEntity> root = org.mockito.Mockito.mock(Root.class);
        CriteriaQuery query = org.mockito.Mockito.mock(CriteriaQuery.class);
        CriteriaBuilder cb = org.mockito.Mockito.mock(CriteriaBuilder.class);
        Join join = org.mockito.Mockito.mock(Join.class);
        Path path = org.mockito.Mockito.mock(Path.class);
        Predicate pred = org.mockito.Mockito.mock(Predicate.class);

        lenient().when(query.getResultType()).thenReturn((Class) StudyGroupEntity.class);
        lenient().when(root.join(anyString(), any(JoinType.class))).thenReturn(join);
        lenient().when(root.get(anyString())).thenReturn(path);
        lenient().when(join.get(anyString())).thenReturn(path);
        lenient().when(cb.equal(any(Expression.class), any())).thenReturn(pred);
        lenient().when(cb.equal(any(), any())).thenReturn(pred);
        lenient().when(cb.and(any(Predicate.class), any(Predicate.class))).thenReturn(pred);
        lenient().when(cb.and(any(Predicate[].class))).thenReturn(pred);
        lenient().when(cb.and(any(), any())).thenReturn(pred);
        lenient().when(cb.lower(any())).thenReturn(path);
        lenient().when(cb.like(any(), anyString())).thenReturn(pred);
        lenient().when(cb.disjunction()).thenReturn(pred);
        lenient().when(cb.or(any(Predicate.class), any(Predicate.class), any(Predicate.class))).thenReturn(pred);
        lenient().when(cb.or(any(Predicate[].class))).thenReturn(pred);
        lenient().when(cb.or(any(), any(), any())).thenReturn(pred);

        spec1.toPredicate(root, query, cb);
        verify(query).distinct(true);

        // 2. Call getGroups with null courseId and blank keyword (Count query using Long.class)
        groupService.getGroups(null, "   ", PageRequest.of(0, 10));
        Specification<StudyGroupEntity> spec2 = captor.getValue();
        lenient().when(query.getResultType()).thenReturn((Class) Long.class);
        spec2.toPredicate(root, query, cb);

        // 3. Call getGroups with null courseId and null keyword (Count query using long.class)
        groupService.getGroups(null, null, PageRequest.of(0, 10));
        Specification<StudyGroupEntity> spec3 = captor.getValue();
        lenient().when(query.getResultType()).thenReturn((Class) long.class);
        spec3.toPredicate(root, query, cb);

        // 4. Call getGroups with special chars only keyword
        groupService.getGroups(courseId, "!@#$", PageRequest.of(0, 10));
        Specification<StudyGroupEntity> spec4 = captor.getValue();
        spec4.toPredicate(root, query, cb);
    }

    @Test
    void getGroupDetail_returnsGroupDetailWithMembers() {
        StudyGroupEntity group = groupEntity(groupId, courseEntity(instructorId), instructorUser());
        GroupMemberEntity member = memberEntity(group, studentUser(studentId, "student@example.com"));
        when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(principal(instructorId, UserRole.INSTRUCTOR)));
        when(studyGroupRepository.findByIdAndInstructorId(groupId, instructorId)).thenReturn(Optional.of(group));
        when(groupMemberRepository.findByGroupIdWithStudent(groupId)).thenReturn(List.of(member));

        GroupDetailResponse result = groupService.getGroupDetail(groupId);

        assertThat(result.getId()).isEqualTo(groupId);
        assertThat(result.getMembers()).hasSize(1);
        assertThat(result.getMembers().getFirst().getStudentId()).isEqualTo(studentId);
    }

    @Test
    void createGroup_throwsNotFound_whenCourseDoesNotExist() {
        CreateGroupRequest request = createRequest();
        when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(principal(instructorId, UserRole.INSTRUCTOR)));
        when(courseRepository.findById(courseId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> groupService.createGroup(request))
                .isInstanceOf(BusinessException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.NOT_FOUND);

        verify(studyGroupRepository, never()).save(any());
    }

    @Test
    void addMembers_throwsBadRequest_whenEmailListEmpty() {
        StudyGroupEntity group = groupEntity(groupId, courseEntity(instructorId), instructorUser());
        AddGroupMembersRequest request = new AddGroupMembersRequest();
        request.setEmails(List.of());

        when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(principal(instructorId, UserRole.INSTRUCTOR)));
        when(studyGroupRepository.findByIdAndInstructorId(groupId, instructorId)).thenReturn(Optional.of(group));

        assertThatThrownBy(() -> groupService.addMembers(groupId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("empty");

        verify(groupMemberRepository, never()).saveAll(anyList());
    }

    @Test
    void addMembers_throwsNotFound_whenEmailDoesNotMatchAnyStudent() {
        StudyGroupEntity group = groupEntity(groupId, courseEntity(instructorId), instructorUser());
        AddGroupMembersRequest request = addMembersRequest("unknown@example.com");

        when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(principal(instructorId, UserRole.INSTRUCTOR)));
        when(studyGroupRepository.findByIdAndInstructorId(groupId, instructorId)).thenReturn(Optional.of(group));
        when(userRepository.findByEmailIgnoreCaseInAndDeletedAtIsNull(List.of("unknown@example.com")))
                .thenReturn(List.of());

        assertThatThrownBy(() -> groupService.addMembers(groupId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void createGroup_savesGroup_whenCourseBelongsToInstructor() {
        Course course = courseEntity(instructorId);
        UserEntity instructor = instructorUser();
        CreateGroupRequest request = createRequest();

        when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(principal(instructorId, UserRole.INSTRUCTOR)));
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(userRepository.getReferenceById(instructorId)).thenReturn(instructor);
        when(studyGroupRepository.save(any(StudyGroupEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        GroupResponse result = groupService.createGroup(request);

        ArgumentCaptor<StudyGroupEntity> captor = ArgumentCaptor.forClass(StudyGroupEntity.class);
        verify(studyGroupRepository).save(captor.capture());
        verify(chatRoomService).getOrCreateRoomForGroup(captor.getValue(), instructor);
        assertThat(captor.getValue().getCourse()).isSameAs(course);
        assertThat(captor.getValue().getInstructor()).isSameAs(instructor);
        assertThat(captor.getValue().getName()).isEqualTo("Group A");
        assertThat(result.getCourseId()).isEqualTo(courseId);
    }

    @Test
    void createGroup_allowsAdminToCreateGroupForAnyInstructorCourse() {
        Course course = courseEntity(otherInstructorId);
        CreateGroupRequest request = createRequest();

        when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(principal(instructorId, UserRole.ADMIN)));
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(userRepository.getReferenceById(instructorId)).thenReturn(instructorUser());
        when(studyGroupRepository.save(any(StudyGroupEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        GroupResponse result = groupService.createGroup(request);

        assertThat(result).isNotNull();
        verify(studyGroupRepository).save(any());
    }

    @Test
    void createGroup_throwsForbidden_whenInstructorCreatesGroupForAnotherInstructorCourse() {
        CreateGroupRequest request = createRequest();
        when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(principal(instructorId, UserRole.INSTRUCTOR)));
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(courseEntity(otherInstructorId)));

        assertThatThrownBy(() -> groupService.createGroup(request))
                .isInstanceOf(BusinessException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.FORBIDDEN);

        verify(studyGroupRepository, never()).save(any());
    }

    @Test
    void createGroup_throwsBadRequest_whenEndDateBeforeStartDate() {
        CreateGroupRequest request = createRequest();
        request.setStartDate(LocalDate.now().plusDays(5));
        request.setEndDate(LocalDate.now().plusDays(1));
        when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(principal(instructorId, UserRole.INSTRUCTOR)));
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(courseEntity(instructorId)));

        assertThatThrownBy(() -> groupService.createGroup(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("End date");

        verify(studyGroupRepository, never()).save(any());
    }

    @Test
    void createGroup_throwsBadRequest_whenStartDateIsInPast() {
        CreateGroupRequest request = createRequest();
        request.setStartDate(LocalDate.now().minusDays(1));
        request.setEndDate(LocalDate.now().plusDays(10));
        when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(principal(instructorId, UserRole.INSTRUCTOR)));
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(courseEntity(instructorId)));

        assertThatThrownBy(() -> groupService.createGroup(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Start date");

        verify(studyGroupRepository, never()).save(any());
    }

    @Test
    void updateGroup_updatesEditableFields() {
        StudyGroupEntity group = groupEntity(groupId, courseEntity(instructorId), instructorUser());
        UpdateGroupRequest request = new UpdateGroupRequest();
        request.setName("Updated");
        request.setDescription("New desc");
        request.setMaxCapacity(30);
        request.setStartDate(LocalDate.now().plusDays(2));
        request.setEndDate(LocalDate.now().plusDays(12));

        when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(principal(instructorId, UserRole.INSTRUCTOR)));
        when(studyGroupRepository.findByIdAndInstructorId(groupId, instructorId)).thenReturn(Optional.of(group));

        GroupResponse result = groupService.updateGroup(groupId, request);

        verify(studyGroupRepository).save(group);
        assertThat(group.getName()).isEqualTo("Updated");
        assertThat(group.getDescription()).isEqualTo("New desc");
        assertThat(group.getMaxCapacity()).isEqualTo(30);
        assertThat(result.getName()).isEqualTo("Updated");
    }

    @Test
    void updateGroup_throwsBadRequest_whenMaxCapacityLessThanCurrentMembers() {
        StudyGroupEntity group = groupEntity(groupId, courseEntity(instructorId), instructorUser());
        UpdateGroupRequest request = new UpdateGroupRequest();
        request.setName("Updated");
        request.setMaxCapacity(1);
        request.setStartDate(LocalDate.now().minusDays(1));
        request.setEndDate(LocalDate.now().plusDays(10));

        when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(principal(instructorId, UserRole.INSTRUCTOR)));
        when(studyGroupRepository.findByIdAndInstructorId(groupId, instructorId)).thenReturn(Optional.of(group));
        when(groupMemberRepository.countByGroupId(groupId)).thenReturn(2L);

        assertThatThrownBy(() -> groupService.updateGroup(groupId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("current member count");

        verify(studyGroupRepository, never()).save(any());
    }

    @Test
    void deleteGroup_deletesMembersBeforeGroup() {
        StudyGroupEntity group = groupEntity(groupId, courseEntity(instructorId), instructorUser());
        when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(principal(instructorId, UserRole.INSTRUCTOR)));
        when(studyGroupRepository.findByIdAndInstructorId(groupId, instructorId)).thenReturn(Optional.of(group));

        groupService.deleteGroup(groupId);

        verify(groupMemberRepository).deleteByGroupId(groupId);
        verify(chatRoomService).deleteRoomForGroup(groupId);
        verify(studyGroupRepository).delete(group);
    }

    @Test
    void deleteGroup_notifiesMembersBeforeDeletingGroup() {
        StudyGroupEntity group = groupEntity(groupId, courseEntity(instructorId), instructorUser());
        UserEntity student = studentUser(studentId, "student@example.com");
        GroupMemberEntity member = memberEntity(group, student);
        when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(principal(instructorId, UserRole.INSTRUCTOR)));
        when(studyGroupRepository.findByIdAndInstructorId(groupId, instructorId)).thenReturn(Optional.of(group));
        when(groupMemberRepository.findByGroupIdWithStudent(groupId)).thenReturn(List.of(member));
        when(notificationPreferenceService.isInAppEnabled(studentId, NotificationType.GROUP_DELETED.name()))
                .thenReturn(true);

        groupService.deleteGroup(groupId);

        verify(notificationService).createNotification(
                studentId,
                NotificationType.GROUP_DELETED.name(),
                "Nhóm học đã bị xoá",
                "Nhóm \"Group A\" của khóa học \"React Basics\" đã bị xoá.",
                "GROUP",
                groupId,
                instructorId,
                "instructor@example.com",
                "group-deleted:" + groupId + ":" + studentId
        );
    }

    @Test
    void addMembers_savesMembers_whenEmailsAreValid() {
        StudyGroupEntity group = groupEntity(groupId, courseEntity(instructorId), instructorUser());
        group.setMaxCapacity(5);
        UserEntity student = studentUser(studentId, "student@example.com");
        AddGroupMembersRequest request = addMembersRequest(" student@example.com ");

        when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(principal(instructorId, UserRole.INSTRUCTOR)));
        when(studyGroupRepository.findByIdAndInstructorId(groupId, instructorId)).thenReturn(Optional.of(group));
        when(userRepository.findByEmailIgnoreCaseInAndDeletedAtIsNull(List.of("student@example.com")))
                .thenReturn(List.of(student));
        when(groupMemberRepository.countByGroupId(groupId)).thenReturn(0L);
        when(groupMemberRepository.findExistingStudentIds(eq(groupId), anyList())).thenReturn(List.of());
        when(chatRoomService.getOrCreateRoomForGroup(group, group.getInstructor())).thenReturn(chatRoomEntity(group));
        when(notificationPreferenceService.isInAppEnabled(studentId, NotificationType.GROUP_MEMBER_ADDED.name()))
                .thenReturn(true);

        List<GroupMemberResponse> result = groupService.addMembers(groupId, request);

        verify(groupMemberRepository).saveAll(anyList());
        verify(chatRoomService).addMember(groupId, student, ChatRoomMemberEntity.MemberRole.MEMBER);
        verify(notificationService).createNotification(
                studentId,
                NotificationType.GROUP_MEMBER_ADDED.name(),
                "Bạn đã được thêm vào nhóm học",
                "Bạn đã được thêm vào nhóm \"Group A\" của khóa học \"React Basics\".",
                "GROUP",
                groupId,
                instructorId,
                "instructor@example.com",
                "group-member-added:" + groupId + ":" + studentId
        );
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getStudentEmail()).isEqualTo("student@example.com");
    }

    @Test
    void addMembers_skipsNotification_whenStudentDisabledPreference() {
        StudyGroupEntity group = groupEntity(groupId, courseEntity(instructorId), instructorUser());
        UserEntity student = studentUser(studentId, "student@example.com");
        AddGroupMembersRequest request = addMembersRequest("student@example.com");

        when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(principal(instructorId, UserRole.INSTRUCTOR)));
        when(studyGroupRepository.findByIdAndInstructorId(groupId, instructorId)).thenReturn(Optional.of(group));
        when(userRepository.findByEmailIgnoreCaseInAndDeletedAtIsNull(List.of("student@example.com")))
                .thenReturn(List.of(student));
        when(groupMemberRepository.countByGroupId(groupId)).thenReturn(0L);
        when(groupMemberRepository.findExistingStudentIds(eq(groupId), anyList())).thenReturn(List.of());
        when(chatRoomService.getOrCreateRoomForGroup(group, group.getInstructor())).thenReturn(chatRoomEntity(group));
        when(notificationPreferenceService.isInAppEnabled(studentId, NotificationType.GROUP_MEMBER_ADDED.name()))
                .thenReturn(false);

        groupService.addMembers(groupId, request);

        verify(groupMemberRepository).saveAll(anyList());
        verify(chatRoomService).addMember(groupId, student, ChatRoomMemberEntity.MemberRole.MEMBER);
        verify(notificationService, never()).createNotification(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void addMembers_throwsBadRequest_whenCapacityExceeded() {
        StudyGroupEntity group = groupEntity(groupId, courseEntity(instructorId), instructorUser());
        group.setMaxCapacity(1);
        AddGroupMembersRequest request = addMembersRequest("student@example.com");

        when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(principal(instructorId, UserRole.INSTRUCTOR)));
        when(studyGroupRepository.findByIdAndInstructorId(groupId, instructorId)).thenReturn(Optional.of(group));
        when(userRepository.findByEmailIgnoreCaseInAndDeletedAtIsNull(List.of("student@example.com")))
                .thenReturn(List.of(studentUser(studentId, "student@example.com")));
        when(groupMemberRepository.countByGroupId(groupId)).thenReturn(1L);

        assertThatThrownBy(() -> groupService.addMembers(groupId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("capacity");

        verify(groupMemberRepository, never()).saveAll(anyList());
    }

    @Test
    void addMembers_throwsConflict_whenStudentAlreadyMember() {
        StudyGroupEntity group = groupEntity(groupId, courseEntity(instructorId), instructorUser());
        UserEntity student = studentUser(studentId, "student@example.com");
        AddGroupMembersRequest request = addMembersRequest("student@example.com");

        when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(principal(instructorId, UserRole.INSTRUCTOR)));
        when(studyGroupRepository.findByIdAndInstructorId(groupId, instructorId)).thenReturn(Optional.of(group));
        when(userRepository.findByEmailIgnoreCaseInAndDeletedAtIsNull(List.of("student@example.com")))
                .thenReturn(List.of(student));
        when(groupMemberRepository.countByGroupId(groupId)).thenReturn(0L);
        when(groupMemberRepository.findExistingStudentIds(eq(groupId), anyList())).thenReturn(List.of(studentId));
        when(userRepository.findById(studentId)).thenReturn(Optional.of(student));

        assertThatThrownBy(() -> groupService.addMembers(groupId, request))
                .isInstanceOf(BusinessException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void updateGroup_notifiesMembers_whenScheduleChanged() {
        StudyGroupEntity group = groupEntity(groupId, courseEntity(instructorId), instructorUser());
        UserEntity student = studentUser(studentId, "student@example.com");
        UpdateGroupRequest request = new UpdateGroupRequest();
        request.setName("Group A");
        request.setDescription("Description");
        request.setMaxCapacity(20);
        request.setStartDate(LocalDate.now().plusDays(2));
        request.setEndDate(LocalDate.now().plusDays(12));

        when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(principal(instructorId, UserRole.INSTRUCTOR)));
        when(studyGroupRepository.findByIdAndInstructorId(groupId, instructorId)).thenReturn(Optional.of(group));
        when(groupMemberRepository.countByGroupId(groupId)).thenReturn(1L);
        when(groupMemberRepository.findByGroupIdWithStudent(groupId)).thenReturn(List.of(memberEntity(group, student)));
        when(notificationPreferenceService.isInAppEnabled(studentId, NotificationType.GROUP_SCHEDULE_CHANGED.name()))
                .thenReturn(true);

        groupService.updateGroup(groupId, request);

        verify(notificationService).createNotification(
                eq(studentId),
                eq(NotificationType.GROUP_SCHEDULE_CHANGED.name()),
                eq("Lịch nhóm học đã thay đổi"),
                any(),
                eq("GROUP"),
                eq(groupId),
                eq(instructorId),
                eq("instructor@example.com"),
                any()
        );
    }

    @Test
    void removeMember_notifiesStudentBeforeRemoving() {
        StudyGroupEntity group = groupEntity(groupId, courseEntity(instructorId), instructorUser());
        UserEntity student = studentUser(studentId, "student@example.com");
        when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(principal(instructorId, UserRole.INSTRUCTOR)));
        when(studyGroupRepository.findByIdAndInstructorId(groupId, instructorId)).thenReturn(Optional.of(group));
        when(groupMemberRepository.findByGroupIdAndStudentIdWithStudent(groupId, studentId))
                .thenReturn(Optional.of(memberEntity(group, student)));
        when(chatRoomService.getOrCreateRoomForGroup(group, group.getInstructor())).thenReturn(chatRoomEntity(group));
        when(notificationPreferenceService.isInAppEnabled(studentId, NotificationType.GROUP_MEMBER_REMOVED.name()))
                .thenReturn(true);

        groupService.removeMember(groupId, studentId);

        verify(notificationService).createNotification(
                studentId,
                NotificationType.GROUP_MEMBER_REMOVED.name(),
                "Bạn đã được xoá khỏi nhóm học",
                "Bạn đã được xoá khỏi nhóm \"Group A\" của khóa học \"React Basics\".",
                "GROUP",
                groupId,
                instructorId,
                "instructor@example.com",
                "group-member-removed:" + groupId + ":" + studentId
        );
        verify(groupMemberRepository).deleteByGroupIdAndStudentId(groupId, studentId);
        verify(chatRoomService).removeMember(groupId, studentId);
    }

    @Test
    void removeMember_throwsNotFound_whenStudentIsNotMember() {
        StudyGroupEntity group = groupEntity(groupId, courseEntity(instructorId), instructorUser());
        when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(principal(instructorId, UserRole.INSTRUCTOR)));
        when(studyGroupRepository.findByIdAndInstructorId(groupId, instructorId)).thenReturn(Optional.of(group));
        when(groupMemberRepository.findByGroupIdAndStudentIdWithStudent(groupId, studentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> groupService.removeMember(groupId, studentId))
                .isInstanceOf(BusinessException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getStudentGroups_returnsGroupsForCurrentStudent() {
        StudyGroupEntity group = groupEntity(groupId, courseEntity(instructorId), instructorUser());
        when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(principal(studentId, UserRole.STUDENT)));
        when(studyGroupRepository.findByStudentId(studentId)).thenReturn(List.of(group));

        List<GroupResponse> result = groupService.getStudentGroups();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getId()).isEqualTo(groupId);
    }

    @Test
    void getStudentGroups_computesActiveStatus_whenEndDateIsNull() {
        StudyGroupEntity group = groupEntity(groupId, courseEntity(instructorId), instructorUser());
        group.setEndDate(null);
        group.setStartDate(LocalDate.now().minusDays(1));
        when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(principal(studentId, UserRole.STUDENT)));
        when(studyGroupRepository.findByStudentId(studentId)).thenReturn(List.of(group));

        List<GroupResponse> result = groupService.getStudentGroups();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void getStudentGroups_computesUpcomingStatus_whenStartDateInFuture() {
        StudyGroupEntity group = groupEntity(groupId, courseEntity(instructorId), instructorUser());
        group.setStartDate(LocalDate.now().plusDays(1));
        group.setEndDate(LocalDate.now().plusDays(10));
        when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(principal(studentId, UserRole.STUDENT)));
        when(studyGroupRepository.findByStudentId(studentId)).thenReturn(List.of(group));

        List<GroupResponse> result = groupService.getStudentGroups();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getStatus()).isEqualTo("UPCOMING");
    }

    @Test
    void getStudentGroups_computesCompletedStatus_whenEndDateInPast() {
        StudyGroupEntity group = groupEntity(groupId, courseEntity(instructorId), instructorUser());
        group.setStartDate(LocalDate.now().minusDays(10));
        group.setEndDate(LocalDate.now().minusDays(1));
        when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(principal(studentId, UserRole.STUDENT)));
        when(studyGroupRepository.findByStudentId(studentId)).thenReturn(List.of(group));

        List<GroupResponse> result = groupService.getStudentGroups();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getStatus()).isEqualTo("COMPLETED");
    }

    @Test
    void getStudentGroupDetail_throwsForbidden_whenCurrentStudentIsNotMember() {
        StudyGroupEntity group = groupEntity(groupId, courseEntity(instructorId), instructorUser());
        when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(principal(studentId, UserRole.STUDENT)));
        when(studyGroupRepository.findByIdWithCourse(groupId)).thenReturn(Optional.of(group));
        when(groupMemberRepository.existsByGroupIdAndStudentId(groupId, studentId)).thenReturn(false);

        assertThatThrownBy(() -> groupService.getStudentGroupDetail(groupId))
                .isInstanceOf(BusinessException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void getStudentGroupDetail_returnsMembers_whenCurrentStudentIsMember() {
        StudyGroupEntity group = groupEntity(groupId, courseEntity(instructorId), instructorUser());
        GroupMemberEntity member = memberEntity(group, studentUser(studentId, "student@example.com"));
        when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(principal(studentId, UserRole.STUDENT)));
        when(studyGroupRepository.findByIdWithCourse(groupId)).thenReturn(Optional.of(group));
        when(groupMemberRepository.existsByGroupIdAndStudentId(groupId, studentId)).thenReturn(true);
        when(groupMemberRepository.findByGroupIdWithStudent(groupId)).thenReturn(List.of(member));

        GroupDetailResponse result = groupService.getStudentGroupDetail(groupId);

        assertThat(result.getMembers()).hasSize(1);
        assertThat(result.getMembers().getFirst().getStudentId()).isEqualTo(studentId);
        assertThat(result.getMembers().getFirst().getStudentEmail()).isNull();
    }

    @Test
    void searchStudentsByEmail_returnsEmpty_whenEmailBlank() {
        assertThat(groupService.searchStudentsByEmail("  ")).isEmpty();
        verify(userRepository, never()).searchStudentsByEmail(any(), any());
    }

    @Test
    void searchStudentsByEmail_mapsStudentResults() {
        UserEntity student = studentUser(studentId, "student@example.com");
        when(userRepository.searchStudentsByEmail("student", UserRole.STUDENT)).thenReturn(List.of(student));

        List<StudentSearchResponse> result = groupService.searchStudentsByEmail(" student ");

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getId()).isEqualTo(studentId);
        assertThat(result.getFirst().getEmail()).isEqualTo("student@example.com");
    }

    private CreateGroupRequest createRequest() {
        CreateGroupRequest request = new CreateGroupRequest();
        request.setCourseId(courseId);
        request.setName(" Group A ");
        request.setDescription("Description");
        request.setMaxCapacity(20);
        request.setStartDate(LocalDate.now().plusDays(1));
        request.setEndDate(LocalDate.now().plusDays(10));
        return request;
    }

    private AddGroupMembersRequest addMembersRequest(String email) {
        AddGroupMembersRequest request = new AddGroupMembersRequest();
        request.setEmails(List.of(email));
        return request;
    }

    private Course courseEntity(UUID ownerId) {
        return Course.builder()
                .id(courseId)
                .instructorId(ownerId)
                .title("React Basics")
                .slug("react-basics")
                .build();
    }

    private StudyGroupEntity groupEntity(UUID id, Course course, UserEntity instructor) {
        StudyGroupEntity group = new StudyGroupEntity();
        group.setId(id);
        group.setCourse(course);
        group.setInstructor(instructor);
        group.setName("Group A");
        group.setDescription("Description");
        group.setMaxCapacity(20);
        group.setStartDate(LocalDate.now().minusDays(1));
        group.setEndDate(LocalDate.now().plusDays(10));
        group.setCreatedAt(OffsetDateTime.now());
        group.setUpdatedAt(OffsetDateTime.now());
        return group;
    }

    private GroupMemberEntity memberEntity(StudyGroupEntity group, UserEntity student) {
        GroupMemberEntity member = new GroupMemberEntity();
        member.setId(UUID.randomUUID());
        member.setGroup(group);
        member.setStudent(student);
        member.setJoinedAt(OffsetDateTime.now());
        return member;
    }

    private ChatRoomEntity chatRoomEntity(StudyGroupEntity group) {
        return ChatRoomEntity.builder()
                .id(group.getId())
                .name(group.getName())
                .group(group)
                .createdBy(group.getInstructor())
                .active(true)
                .build();
    }

    private UserEntity instructorUser() {
        return userEntity(instructorId, "instructor@example.com", UserRole.INSTRUCTOR);
    }

    private UserEntity studentUser(UUID id, String email) {
        return userEntity(id, email, UserRole.STUDENT);
    }

    private UserEntity userEntity(UUID id, String email, UserRole role) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setEmail(email);
        user.setFullName(role == UserRole.STUDENT ? "Student One" : "Instructor One");
        user.setPasswordHash("hash");
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }

    private UserPrincipal principal(UUID id, UserRole role) {
        return new UserPrincipal(userEntity(id, role.name().toLowerCase() + "@example.com", role));
    }
}
