import { NotificationTypeMetadata, getNotificationTargetUrl, parseNotificationUrl } from '../../constants/notification-types';
import { useAuthStore } from '../../store';

(() => {
  const { useState, useEffect, useCallback, useMemo } = React;
  const Ic = window.Icon;

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
    referenceType?: string | null;
    referenceId?: string | null;
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
      ? notifications.filter(n => n.type === filter || (NotificationTypeMetadata[n.type] && NotificationTypeMetadata[n.type].category === filter))
      : notifications;

    const loadMore = () => {
      if (page < totalPages - 1) load(page + 1, false);
    };

    const handleNotificationClick = async (n: NotificationItem) => {
      if (!n.read) await handleMarkAsRead(n.id);
      const role = useAuthStore.getState().role || 'student';
      const targetUrl = getNotificationTargetUrl(n, role);
      if (window.AppShell && typeof window.AppShell.go === 'function') {
        const { routeKey, params } = parseNotificationUrl(targetUrl);
        window.AppShell.go(routeKey, Object.keys(params).length > 0 ? params : undefined);
      } else {
        window.location.href = targetUrl;
      }
    };

    const categories = useMemo(() => {
      const cats = new Map<string, { label: string; color: string; icon: string; types: string[] }>();
      Object.entries(NotificationTypeMetadata).forEach(([type, meta]) => {
        const cat = meta.category || 'Hệ thống';
        if (!cats.has(cat)) cats.set(cat, { label: cat, color: meta.color, icon: meta.icon, types: [] });
        cats.get(cat)!.types.push(type);
      });
      return Array.from(cats.values());
    }, []);

    const [expandedCat, setExpandedCat] = useState<string | null>(null);

    const filterOptions = useMemo(() => Object.entries(NotificationTypeMetadata).map(([type, meta]) => ({
      type,
      label: meta.label,
      icon: meta.icon,
      color: meta.color,
      category: meta.category,
    })), []);

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

          <div style={{ marginTop: 16, display: 'flex', flexDirection: 'column', gap: 8 }}>
            <div className="row gap-8" style={{ flexWrap: 'wrap' }}>
              <button className={'chip' + (filter === null ? ' chip-info' : ' chip-neutral')} onClick={() => { setFilter(null); setExpandedCat(null); }}>
                Tất cả ({notifications.length})
              </button>
              {categories.map((cat) => {
                const isExpanded = expandedCat === cat.label;
                const isCatFilter = filter !== null && cat.types.includes(filter);
                return (
                  <button key={cat.label}
                    className={'chip' + (isExpanded || isCatFilter ? ' chip-info' : ' chip-neutral')}
                    onClick={() => setExpandedCat(isExpanded ? null : cat.label)}
                    style={{ borderColor: isExpanded || isCatFilter ? cat.color : undefined }}
                  >
                    <Ic n={cat.icon} size={14} /> {cat.label}
                  </button>
                );
              })}
            </div>
            {expandedCat && (
              <div className="row gap-6" style={{ flexWrap: 'wrap', paddingLeft: 8 }}>
                {filterOptions.filter(o => o.category === expandedCat).map((opt) => {
                  const isSelected = filter === opt.type;
                  return (
                    <button key={opt.type}
                      className="chip chip-neutral"
                      style={{
                        background: isSelected ? opt.color + '1a' : undefined,
                        borderColor: isSelected ? opt.color : 'var(--border)',
                        color: isSelected ? opt.color : 'var(--text-2)',
                        fontWeight: isSelected ? 700 : 500,
                        fontSize: 12,
                      }}
                      onClick={() => setFilter(filter === opt.type ? null : opt.type)}
                    >
                      <span style={{ width: 6, height: 6, borderRadius: 999, background: opt.color, display: 'inline-block' }} />
                      <Ic n={opt.icon} size={12} /> {opt.label}
                    </button>
                  );
                })}
              </div>
            )}
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
            const meta = NotificationTypeMetadata[n.type] || { label: n.type, icon: 'bell', color: '#2563eb', category: 'Hệ thống' };
            return (
              <div
                key={n.id}
                className="card"
                style={{
                  padding: 0,
                  background: n.read ? '#fff' : 'var(--accent-soft)',
                  cursor: 'pointer',
                }}
                onClick={() => handleNotificationClick(n)}
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
                    <div className="row gap-8" style={{ marginBottom: 4 }}>
                      <span className="chip" style={{ background: meta.color + '1a', color: meta.color, borderColor: meta.color + '33', fontSize: 11, padding: '1px 6px' }}>{meta.label}</span>
                    </div>
                    <div className="t-sm" style={{ lineHeight: 1.4, fontWeight: n.read ? 400 : 600 }}>
                      {n.title}
                    </div>
                    {n.body && (
                      <div className="t-xs muted" style={{ marginTop: 4, lineHeight: 1.4 }}>{n.body}</div>
                    )}
                    <div className="t-xs dim" style={{ marginTop: 6 }}>{timeAgo(n.createdAt)}</div>
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
