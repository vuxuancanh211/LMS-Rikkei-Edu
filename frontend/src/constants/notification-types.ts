export enum NotificationType {
  FORUM_REPLY = 'FORUM_REPLY',
  FORUM_POST = 'FORUM_POST',
  QUIZ_PUBLISHED = 'QUIZ_PUBLISHED',
  SUBMISSION_GRADED = 'SUBMISSION_GRADED',
  ASSIGNMENT_PUBLISHED = 'ASSIGNMENT_PUBLISHED',
  ASSIGNMENT_SUBMITTED = 'ASSIGNMENT_SUBMITTED',
  CERTIFICATE_ISSUED = 'CERTIFICATE_ISSUED',
  COURSE_ENROLLMENT = 'COURSE_ENROLLMENT',
  COURSE_APPROVED = 'COURSE_APPROVED',
  SYSTEM_ANNOUNCEMENT = 'SYSTEM_ANNOUNCEMENT',
}

export const NotificationTypeLabels: Record<NotificationType, string> = {
  [NotificationType.FORUM_REPLY]: 'Trả lời diễn đàn',
  [NotificationType.FORUM_POST]: 'Bài đăng diễn đàn',
  [NotificationType.QUIZ_PUBLISHED]: 'Bài kiểm tra mới',
  [NotificationType.SUBMISSION_GRADED]: 'Bài làm đã chấm',
  [NotificationType.ASSIGNMENT_PUBLISHED]: 'Bài tập mới',
  [NotificationType.ASSIGNMENT_SUBMITTED]: 'Bài tập đã nộp',
  [NotificationType.CERTIFICATE_ISSUED]: 'Chứng chỉ',
  [NotificationType.COURSE_ENROLLMENT]: 'Ghi danh khóa học',
  [NotificationType.COURSE_APPROVED]: 'Khóa học được duyệt',
  [NotificationType.SYSTEM_ANNOUNCEMENT]: 'Thông báo hệ thống',
};
