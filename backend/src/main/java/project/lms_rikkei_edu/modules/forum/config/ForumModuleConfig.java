package project.lms_rikkei_edu.modules.forum.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import project.lms_rikkei_edu.common.security.CurrentUserProvider;
import project.lms_rikkei_edu.modules.forum.repository.ForumCourseRepository;
import project.lms_rikkei_edu.modules.forum.repository.ForumPostRepository;
import project.lms_rikkei_edu.modules.forum.repository.ForumReactionRepository;
import project.lms_rikkei_edu.modules.forum.repository.ForumReportRepository;
import project.lms_rikkei_edu.modules.forum.repository.ForumReplyRepository;
import project.lms_rikkei_edu.modules.forum.service.ForumAttachmentService;
import project.lms_rikkei_edu.modules.forum.service.ForumService;
import project.lms_rikkei_edu.modules.forum.service.impl.ForumServiceImpl;
import project.lms_rikkei_edu.modules.notification.service.NotificationPreferenceService;
import project.lms_rikkei_edu.modules.notification.service.NotificationService;
import project.lms_rikkei_edu.modules.user.repository.UserRepository;

@Configuration
public class ForumModuleConfig {

    @Bean
    public ForumService forumService(
            ForumPostRepository forumPostRepository,
            ForumReplyRepository forumReplyRepository,
            ForumCourseRepository forumCourseRepository,
            ForumReactionRepository forumReactionRepository,
            ForumReportRepository forumReportRepository,
            ForumAttachmentService forumAttachmentService,
            NotificationService notificationService,
            NotificationPreferenceService notificationPreferenceService,
            UserRepository userRepository,
            CurrentUserProvider currentUserProvider) {
        return new ForumServiceImpl(
                forumPostRepository,
                forumReplyRepository,
                forumCourseRepository,
                forumReactionRepository,
                forumReportRepository,
                forumAttachmentService,
                notificationService,
                notificationPreferenceService,
                userRepository,
                currentUserProvider);
    }
}
