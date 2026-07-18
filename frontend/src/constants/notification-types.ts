export enum NotificationType {
  FORUM_REPLY = 'FORUM_REPLY',
  FORUM_POST = 'FORUM_POST',
  QUIZ_PUBLISHED = 'QUIZ_PUBLISHED',
  SUBMISSION_GRADED = 'SUBMISSION_GRADED',
  ASSIGNMENT_PUBLISHED = 'ASSIGNMENT_PUBLISHED',
  ASSIGNMENT_SUBMITTED = 'ASSIGNMENT_SUBMITTED',
  CERTIFICATE_ISSUED = 'CERTIFICATE_ISSUED',
  COURSE_ENROLLMENT = 'COURSE_ENROLLMENT',
  GROUP_MEMBER_ADDED = 'GROUP_MEMBER_ADDED',
  GROUP_MEMBER_REMOVED = 'GROUP_MEMBER_REMOVED',
  GROUP_SCHEDULE_CHANGED = 'GROUP_SCHEDULE_CHANGED',
  GROUP_STARTING_SOON = 'GROUP_STARTING_SOON',
  GROUP_STARTED = 'GROUP_STARTED',
  GROUP_ENDING_SOON = 'GROUP_ENDING_SOON',
  GROUP_ENDED = 'GROUP_ENDED',
  GROUP_DELETED = 'GROUP_DELETED',
  COURSE_APPROVED = 'COURSE_APPROVED',
  COURSE_SUBMITTED = 'COURSE_SUBMITTED',
  COURSE_UPDATE_SUBMITTED = 'COURSE_UPDATE_SUBMITTED',
  SYSTEM_ANNOUNCEMENT = 'SYSTEM_ANNOUNCEMENT',
  AI_SOURCE_INDEXED = 'AI_SOURCE_INDEXED',
  AI_SOURCE_FAILED = 'AI_SOURCE_FAILED',
}

export const NotificationTypeLabels: Record<NotificationType, string> = {
  [NotificationType.FORUM_REPLY]: 'Trả lời diễn đàn',
  [NotificationType.FORUM_POST]: 'Bài đăng diễn đàn',
  [NotificationType.QUIZ_PUBLISHED]: 'Bài kiểm tra mới',
  [NotificationType.SUBMISSION_GRADED]: 'Bài làm đã chấm',
  [NotificationType.ASSIGNMENT_PUBLISHED]: 'Bài tập mới',
  [NotificationType.ASSIGNMENT_SUBMITTED]: 'Bài tập đã nộp',
  [NotificationType.CERTIFICATE_ISSUED]: 'Chứng chỉ mới',
  [NotificationType.COURSE_ENROLLMENT]: 'Ghi danh khóa học',
  [NotificationType.GROUP_MEMBER_ADDED]: 'Thêm vào nhóm học',
  [NotificationType.GROUP_MEMBER_REMOVED]: 'Rời nhóm học',
  [NotificationType.GROUP_SCHEDULE_CHANGED]: 'Lịch học thay đổi',
  [NotificationType.GROUP_STARTING_SOON]: 'Nhóm sắp bắt đầu',
  [NotificationType.GROUP_STARTED]: 'Nhóm đã bắt đầu',
  [NotificationType.GROUP_ENDING_SOON]: 'Nhóm sắp kết thúc',
  [NotificationType.GROUP_ENDED]: 'Nhóm đã kết thúc',
  [NotificationType.GROUP_DELETED]: 'Nhóm đã giải tán',
  [NotificationType.COURSE_APPROVED]: 'Khóa học được duyệt',
  [NotificationType.COURSE_SUBMITTED]: 'Khóa học cần duyệt',
  [NotificationType.COURSE_UPDATE_SUBMITTED]: 'Cập nhật khóa học cần duyệt',
  [NotificationType.SYSTEM_ANNOUNCEMENT]: 'Thông báo hệ thống',
  [NotificationType.AI_SOURCE_INDEXED]: 'Cập nhật tri thức AI',
  [NotificationType.AI_SOURCE_FAILED]: 'Lỗi tri thức AI',
};

export const NotificationTypeMetadata: Record<string, { label: string; icon: string; color: string; category: string }> = {
  FORUM_REPLY: { label: 'Trả lời diễn đàn', icon: 'message', color: '#8b5cf6', category: 'Diễn đàn' },
  FORUM_POST: { label: 'Bài đăng diễn đàn', icon: 'message', color: '#6366f1', category: 'Diễn đàn' },
  QUIZ_PUBLISHED: { label: 'Bài kiểm tra mới', icon: 'shield', color: '#f59e0b', category: 'Học tập' },
  SUBMISSION_GRADED: { label: 'Bài làm đã chấm', icon: 'check_circle', color: '#10b981', category: 'Học tập' },
  ASSIGNMENT_PUBLISHED: { label: 'Bài tập mới', icon: 'clipboard', color: '#3b82f6', category: 'Học tập' },
  ASSIGNMENT_SUBMITTED: { label: 'Bài tập đã nộp', icon: 'upload', color: '#06b6d4', category: 'Học tập' },
  CERTIFICATE_ISSUED: { label: 'Chứng chỉ mới', icon: 'award', color: '#ec4899', category: 'Học tập' },
  COURSE_ENROLLMENT: { label: 'Ghi danh khóa học', icon: 'user_plus', color: '#2563eb', category: 'Khóa học' },
  COURSE_APPROVED: { label: 'Khóa học được duyệt', icon: 'check', color: '#16a34a', category: 'Khóa học' },
  COURSE_SUBMITTED: { label: 'Khóa học cần duyệt', icon: 'send', color: '#d97706', category: 'Khóa học' },
  COURSE_UPDATE_SUBMITTED: { label: 'Cập nhật khóa học cần duyệt', icon: 'send', color: '#d97706', category: 'Khóa học' },
  GROUP_MEMBER_ADDED: { label: 'Thêm vào nhóm học', icon: 'users', color: '#0ea5e9', category: 'Nhóm học' },
  GROUP_MEMBER_REMOVED: { label: 'Rời nhóm học', icon: 'user_minus', color: '#ef4444', category: 'Nhóm học' },
  GROUP_SCHEDULE_CHANGED: { label: 'Lịch học thay đổi', icon: 'calendar', color: '#f97316', category: 'Nhóm học' },
  GROUP_STARTING_SOON: { label: 'Nhóm sắp bắt đầu', icon: 'clock', color: '#eab308', category: 'Nhóm học' },
  GROUP_STARTED: { label: 'Nhóm đã bắt đầu', icon: 'play', color: '#22c55e', category: 'Nhóm học' },
  GROUP_ENDING_SOON: { label: 'Nhóm sắp kết thúc', icon: 'clock', color: '#f43f5e', category: 'Nhóm học' },
  GROUP_ENDED: { label: 'Nhóm đã kết thúc', icon: 'stop', color: '#64748b', category: 'Nhóm học' },
  GROUP_DELETED: { label: 'Nhóm đã giải tán', icon: 'trash', color: '#94a3b8', category: 'Nhóm học' },
  SYSTEM_ANNOUNCEMENT: { label: 'Thông báo hệ thống', icon: 'bell', color: '#d97706', category: 'Hệ thống' },
  AI_SOURCE_INDEXED: { label: 'Cập nhật tri thức AI', icon: 'cpu', color: '#8b5cf6', category: 'Hệ thống' },
  AI_SOURCE_FAILED: { label: 'Lỗi tri thức AI', icon: 'alert_circle', color: '#dc2626', category: 'Hệ thống' },
};

export function parseNotificationUrl(targetUrl: string): { routeKey: string; params: Record<string, string> } {
  const base = targetUrl.replace(/^\/[^/]+\//, '').replace(/^\//, '');
  const [pathPart, queryString] = base.split('?');
  const segments = pathPart.split('/');
  const params: Record<string, string> = {};
  const routeKey = segments[0];
  if (segments.length > 1 && segments[1]) {
    if (routeKey === 'courses') params.courseId = segments[1];
    else if (routeKey === 'groups') params.groupId = segments[1];
    else params.id = segments[1];
  }
  if (queryString) {
    queryString.split('&').forEach(pair => {
      const [k, v] = pair.split('=');
      params[k] = decodeURIComponent(v || '');
    });
  }
  return { routeKey, params };
}

export function getNotificationTargetUrl(n: any, role?: string): string {
  if (!n) return '/notifications';
  const type = n.type || '';
  const refId = n.referenceId;
  const refType = n.referenceType;
  const prefix = role ? `/${role}` : '/student';
  if (type === 'FORUM_POST' || type === 'FORUM_REPLY' || type === 'FORUM_REPLY_ADDED' || refType === 'POST' || refType === 'FORUM_POST') {
    return refId ? `${prefix}/forum?postId=${refId}` : `${prefix}/forum`;
  }
  if (type === 'QUIZ_PUBLISHED' || type === 'ASSIGNMENT_PUBLISHED' || type === 'SUBMISSION_GRADED') {
    if (refType === 'COURSE') return refId ? `${prefix}/courses/${refId}` : `${prefix}/courses`;
    return `${prefix}/notifications`;
  }
  if (type === 'COURSE_SUBMITTED' || type === 'COURSE_UPDATE_SUBMITTED') {
    // Dành cho admin: /admin/courses/{id} không phải route có thật (chỉ có /admin/courses danh
    // sách và /admin/approval), nên trỏ thẳng vào trang phê duyệt kèm approvalId để tự mở đúng
    // khóa học (xem AdminApproval.tsx đọc query approvalId để auto-mở modal chi tiết).
    return refId ? `${prefix}/approval?approvalId=${refId}` : `${prefix}/approval`;
  }
  if (type.startsWith('COURSE_')) {
    return refId ? `${prefix}/courses/${refId}` : `${prefix}/courses`;
  }
  if (type.startsWith('GROUP_')) {
    return refId ? `${prefix}/groups/${refId}` : `${prefix}/groups`;
  }
  if (type === 'CERTIFICATE_ISSUED') {
    return `${prefix}/certificates`;
  }
  return '/notifications';
}
