package project.lms_rikkei_edu.modules.group.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import project.lms_rikkei_edu.modules.course.entity.Course;
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

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupNotificationSchedulerTest {

    @Mock
    private StudyGroupRepository studyGroupRepository;
    @Mock
    private GroupMemberRepository groupMemberRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private NotificationPreferenceService notificationPreferenceService;

    private GroupNotificationScheduler scheduler;
    private final UUID groupId = UUID.randomUUID();
    private final UUID studentId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        scheduler = new GroupNotificationScheduler(
                studyGroupRepository,
                groupMemberRepository,
                notificationService,
                notificationPreferenceService);
    }

    @Test
    void sendDailyGroupNotifications_sendsStartingSoonNotification() {
        LocalDate tomorrow = LocalDate.now(ZoneId.systemDefault()).plusDays(1);
        StudyGroupEntity group = group(tomorrow, tomorrow.plusDays(10));
        when(studyGroupRepository.findByStartDateWithCourse(tomorrow)).thenReturn(List.of(group));
        when(groupMemberRepository.findByGroupIdWithStudent(groupId)).thenReturn(List.of(member(group)));
        when(notificationPreferenceService.isInAppEnabled(studentId, NotificationType.GROUP_STARTING_SOON.name()))
                .thenReturn(true);

        scheduler.sendDailyGroupNotifications();

        verify(notificationService).createNotification(
                eq(studentId),
                eq(NotificationType.GROUP_STARTING_SOON.name()),
                eq("Nhóm học sắp bắt đầu"),
                eq("Nhóm \"Group A\" sẽ bắt đầu vào ngày " + tomorrow + "."),
                eq("GROUP"),
                eq(groupId),
                eq(null),
                eq("Hệ thống"),
                eq("group-starting-soon:" + groupId + ":" + tomorrow + ":" + studentId)
        );
    }

    @Test
    void sendDailyGroupNotifications_skipsWhenPreferenceDisabled() {
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        LocalDate tomorrow = today.plusDays(1);
        StudyGroupEntity group = group(today, today.plusDays(10));
        when(studyGroupRepository.findByStartDateWithCourse(tomorrow)).thenReturn(List.of());
        when(studyGroupRepository.findByStartDateWithCourse(today)).thenReturn(List.of(group));
        when(groupMemberRepository.findByGroupIdWithStudent(groupId)).thenReturn(List.of(member(group)));
        when(notificationPreferenceService.isInAppEnabled(studentId, NotificationType.GROUP_STARTED.name()))
                .thenReturn(false);

        scheduler.sendDailyGroupNotifications();

        verify(notificationService, never())
                .createNotification(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    private StudyGroupEntity group(LocalDate startDate, LocalDate endDate) {
        StudyGroupEntity group = new StudyGroupEntity();
        group.setId(groupId);
        group.setName("Group A");
        group.setStartDate(startDate);
        group.setEndDate(endDate);
        group.setCourse(Course.builder()
                .id(UUID.randomUUID())
                .title("React Basics")
                .slug("react-basics")
                .build());
        return group;
    }

    private GroupMemberEntity member(StudyGroupEntity group) {
        UserEntity student = new UserEntity();
        student.setId(studentId);
        student.setEmail("student@example.com");
        student.setFullName("Student One");
        student.setRole(UserRole.STUDENT);
        student.setStatus(UserStatus.ACTIVE);
        GroupMemberEntity member = new GroupMemberEntity();
        member.setId(UUID.randomUUID());
        member.setGroup(group);
        member.setStudent(student);
        return member;
    }
}
