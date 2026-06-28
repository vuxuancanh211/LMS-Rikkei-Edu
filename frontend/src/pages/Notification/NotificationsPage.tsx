(() => {
  const { useState, useEffect, useCallback } = React;
  const Ic = window.Icon;

  const typeMeta: Record<string, { icon: string; color: string }> = {
    FORUM_REPLY: { icon: 'message', color: '#8b5cf6' },
    FORUM_POST: { icon: 'message', color: '#6366f1' },
    QUIZ_PUBLISHED: { icon: 'shield', color: '#f59e0b' },
    SUBMISSION_GRADED: { icon: 'edit', color: '#10b981' },
    ASSIGNMENT_PUBLISHED: { icon: 'clipboard', color: '#3b82f6' },
    ASSIGNMENT_SUBMITTED: { icon: 'upload', color: '#06b6d4' },
    CERTIFICATE_ISSUED: { icon: 'award', color: '#10b981' },
    COURSE_ENROLLMENT: { icon: 'user_plus', color: '#2563eb' },
    COURSE_APPROVED: { icon: 'check_circle', color: '#16a34a' },
    SYSTEM_ANNOUNCEMENT: { icon: 'bell', color: '#f97316' },
  };

  function timeAgo(value: string): string {
    if (!value) return '';
    const ms = Date.now() - new Date(value).getTime();
    const min = Math.floor(ms / 60000);
    if (min < 1) return 'Vừa xong';
    if (min < 60) return `${min} phút trước`;
    const hr = Math.floor(min / 60);
    if (hr < 24) return `${hr} giờ trước`;
    const day = Math.floor(hr / 24);
    if (day < 7) return `${day} ngày trước`;
    return new Intl.DateTimeFormat('vi-VN', { dateStyle: 'medium' }).format(new Date(value));
  }

  interface NotificationItem {
    id: string;
    type: string;
    title: string;
    body?: string | null;
    read: boolean;
    createdAt: string;
  }

  function NotificationsPage() {
    const [notifications, setNotifications] = useState<NotificationItem[]>([]);
    const [page, setPage] = useState(0);
    const [totalPages, setTotalPages] = useState(0);
    const [loading, setLoading] = useState(true);
    const [filter, setFilter] = useState<string | null>(null);

    const load = useCallback(async (p: number, reset: boolean) => {
      setLoading(true);
      try {
        const { getNotifications } = await import('../../services/notification-service');
        const data = await getNotifications(p, 20);
        setNotifications(prev => reset ? data.content : [...prev, ...data.content]);
        setTotalPages(data.totalPages);
        setPage(p);
      } catch { /* ignore */ }
      setLoading(false);
    }, []);

    useEffect(() => { load(0, true); }, [load]);

    const handleMarkAsRead = async (id: string) => {
      try {
        const { markAsRead } = await import('../../services/notification-service');
        await markAsRead(id);
        setNotifications(prev => prev.map(n => n.id === id ? { ...n, read: true } : n));
      } catch { /* ignore */ }
    };

    const handleMarkAllAsRead = async () => {
      try {
        const { markAllAsRead } = await import('../../services/notification-service');
        await markAllAsRead();
        setNotifications(prev => prev.map(n => ({ ...n, read: true })));
      } catch { /* ignore */ }
    };

    const visible = filter
      ? notifications.filter(n => n.type === filter)
      : notifications;

    const loadMore = () => {
      if (page < totalPages - 1) load(page + 1, false);
    };

    return (
      <div className="page fade-in">
        <div className="page-head">
          <div className="between">
            <div>
              <h1 className="t-h1">Thông báo</h1>
              <p>Tất cả thông báo từ hệ thống.</p>
            </div>
            {notifications.some(n => !n.read) && (
              <button className="btn btn-ghost btn-sm" onClick={handleMarkAllAsRead}>
                <Ic n="check_double" size={16} /> Đánh dấu đã đọc
              </button>
            )}
          </div>

          <div className="row gap-8" style={{ marginTop: 16, flexWrap: 'wrap' }}>
            <button
              className={'chip' + (filter === null ? ' chip-info' : ' chip-neutral')}
              onClick={() => setFilter(null)}
            >
              Tất cả
            </button>
            {Object.entries(typeMeta).map(([type, meta]) => (
              <button
                key={type}
                className={'chip' + (filter === type ? ' chip-info' : ' chip-neutral')}
                onClick={() => setFilter(type)}
              >
                <Ic n={meta.icon} size={14} /> {type.replace(/_/g, ' ')}
              </button>
            ))}
          </div>
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
          {loading && page === 0 && (
            <div className="t-sm muted" style={{ padding: '48px 18px', textAlign: 'center' }}>Đang tải...</div>
          )}
          {!loading && visible.length === 0 && (
            <div className="card card-pad" style={{ textAlign: 'center', padding: 48 }}>
              <Ic n="bell" size={40} style={{ opacity: 0.3, marginBottom: 12 }} />
              <div className="t-sm muted">Không có thông báo nào.</div>
            </div>
          )}
          {visible.map((n) => {
            const meta = typeMeta[n.type] || { icon: 'bell', color: '#2563eb' };
            return (
              <div
                key={n.id}
                className="card"
                style={{
                  padding: 0,
                  background: n.read ? '#fff' : 'var(--accent-soft)',
                  cursor: 'pointer',
                }}
                onClick={() => { if (!n.read) handleMarkAsRead(n.id); }}
              >
                <div className="row gap-12" style={{ padding: '14px 18px' }}>
                  <div
                    className="stat-ic"
                    style={{
                      width: 40,
                      height: 40,
                      borderRadius: 10,
                      background: meta.color + '1a',
                      color: meta.color,
                      flex: 'none',
                    }}
                  >
                    <Ic n={meta.icon} size={18} />
                  </div>
                  <div className="grow" style={{ minWidth: 0 }}>
                    <div className="t-sm" style={{ lineHeight: 1.4, fontWeight: n.read ? 400 : 600 }}>
                      {n.title}
                    </div>
                    {n.body && (
                      <div className="t-xs muted truncate" style={{ marginTop: 3 }}>{n.body}</div>
                    )}
                    <div className="t-xs dim" style={{ marginTop: 4 }}>{timeAgo(n.createdAt)}</div>
                  </div>
                  {!n.read && (
                    <span style={{
                      width: 10,
                      height: 10,
                      borderRadius: 999,
                      background: 'var(--accent)',
                      flex: 'none',
                    }} />
                  )}
                </div>
              </div>
            );
          })}
          {page < totalPages - 1 && (
            <button
              className="btn btn-ghost"
              style={{ marginTop: 8 }}
              onClick={loadMore}
              disabled={loading}
            >
              {loading ? 'Đang tải...' : 'Tải thêm'}
            </button>
          )}
        </div>
      </div>
    );
  }

  window.NotificationsPage = NotificationsPage;
})();
