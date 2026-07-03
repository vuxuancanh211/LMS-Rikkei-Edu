package project.lms_rikkei_edu.modules.group.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.lms_rikkei_edu.modules.group.entity.GroupMemberEntity;
import project.lms_rikkei_edu.modules.group.entity.StudyGroupEntity;
import project.lms_rikkei_edu.modules.group.repository.GroupMemberRepository;
import project.lms_rikkei_edu.modules.group.repository.StudyGroupRepository;
import project.lms_rikkei_edu.modules.notification.enums.NotificationType;
import project.lms_rikkei_edu.modules.notification.service.NotificationPreferenceService;
import project.lms_rikkei_edu.modules.notification.service.NotificationService;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GroupNotificationScheduler {

    private static final String SYSTEM_ACTOR_NAME = "Hệ thống";
    private static final String GROUP_REFERENCE_TYPE = "GROUP";

    private final StudyGroupRepository studyGroupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final NotificationService notificationService;
    private final NotificationPreferenceService notificationPreferenceService;

    @Scheduled(cron = "${app.group.notifications.cron:0 0 8 * * *}")
    @Transactional
    public void sendDailyGroupNotifications() {
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        LocalDate tomorrow = today.plusDays(1);

        notifyGroups(studyGroupRepository.findByStartDateWithCourse(tomorrow), NotificationType.GROUP_STARTING_SOON,
                "Nhóm học sắp bắt đầu",
                group -> "Nhóm \"" + group.getName() + "\" sẽ bắt đầu vào ngày " + group.getStartDate() + ".",
                group -> "group-starting-soon:" + group.getId() + ":" + group.getStartDate());

        notifyGroups(studyGroupRepository.findByStartDateWithCourse(today), NotificationType.GROUP_STARTED,
                "Nhóm học bắt đầu hôm nay",
                group -> "Nhóm \"" + group.getName() + "\" đã bắt đầu hôm nay.",
                group -> "group-started:" + group.getId() + ":" + group.getStartDate());

        notifyGroups(studyGroupRepository.findByEndDateWithCourse(tomorrow), NotificationType.GROUP_ENDING_SOON,
                "Nhóm học sắp kết thúc",
                group -> "Nhóm \"" + group.getName() + "\" sẽ kết thúc vào ngày " + group.getEndDate() + ".",
                group -> "group-ending-soon:" + group.getId() + ":" + group.getEndDate());

        notifyGroups(studyGroupRepository.findByEndDateBeforeWithCourse(today), NotificationType.GROUP_ENDED,
                "Nhóm học đã kết thúc",
                group -> "Nhóm \"" + group.getName() + "\" đã kết thúc. Bạn vẫn có thể xem lại nội dung nhóm.",
                group -> "group-ended:" + group.getId());
    }

    private void notifyGroups(List<StudyGroupEntity> groups, NotificationType type, String title,
                              GroupMessageBuilder bodyBuilder, GroupMessageBuilder keyBuilder) {
        for (StudyGroupEntity group : groups) {
            List<GroupMemberEntity> members = groupMemberRepository.findByGroupIdWithStudent(group.getId());
            String body = bodyBuilder.build(group);
            String keyPrefix = keyBuilder.build(group);
            for (GroupMemberEntity member : members) {
                notifyMember(group, member.getStudent().getId(), type, title, body,
                        keyPrefix + ":" + member.getStudent().getId());
            }
        }
    }

    private void notifyMember(StudyGroupEntity group, UUID studentId, NotificationType type,
                              String title, String body, String idempotencyKey) {
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
                null,
                SYSTEM_ACTOR_NAME,
                idempotencyKey
        );
    }

    @FunctionalInterface
    private interface GroupMessageBuilder {
        String build(StudyGroupEntity group);
    }
}
