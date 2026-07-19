// @ts-nocheck
/* ============================================================
   RIKKEI EDU — Quản trị · Forum
   ============================================================ */
(function () {
  const { useEffect, useMemo, useRef, useState } = React;
  const Ic = window.Icon;
  const { Avatar, Search, Tabs, Select, StatCard, Section, Modal, ModalHead, Empty } = window;
  const forumService = window.__adminForumService;

  const reportReasonMap = {
    SPAM: "Spam / Quảng cáo",
    OFFENSIVE: "Nội dung xúc phạm",
    MISINFORMATION: "Thông tin sai lệch",
    OTHER: "Khác",
  };

  function fmtDate(value) {
    if (!value) return "—";
    return new Intl.DateTimeFormat("vi-VN", { dateStyle: "short", timeStyle: "short" }).format(new Date(value));
  }

  function statusChip(status) {
    const s = String(status || "PENDING").toUpperCase();
    if (s === "RESOLVED") return <span className="chip chip-success">Đã xử lý</span>;
    if (s === "DISMISSED") return <span className="chip chip-neutral">Bỏ qua</span>;
    return <span className="chip chip-warning">Chờ xử lý</span>;
  }

  function topicLabel(topic) {
    const map = { announcement: "Thông báo", qa: "Hỏi đáp", share: "Chia sẻ", idea: "Ý tưởng", discussion: "Thảo luận" };
    return map[topic] || topic || "—";
  }

  function makePg(pageData, page, setPage, size) {
    const total = pageData.totalElements || 0;
    return {
      slice: pageData.content || [],
      page: page + 1,
      pages: Math.max(pageData.totalPages || 1, 1),
      setPage: (next) => setPage(Math.max(0, next - 1)),
      total,
      from: total ? page * size + 1 : 0,
      to: Math.min((page + 1) * size, total),
    };
  }

  function AdminForum() {
    const [tab, setTab] = useState("reports");
    const [q, setQ] = useState("");
    const [debouncedQ, setDebouncedQ] = useState("");
    const [reportStatus, setReportStatus] = useState("all");
    const [postsFilter, setPostsFilter] = useState("all");
    const [postsPage, setPostsPage] = useState({ content: [], totalElements: 0, totalPages: 1 });
    const [reportsPage, setReportsPage] = useState({ content: [], totalElements: 0, totalPages: 1 });
    const [page, setPage] = useState(0);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState(null);
    const [confirm, setConfirm] = useState(null);
    const [snapshotStats, setSnapshotStats] = useState(null);
    const [postsSnapshot, setPostsSnapshot] = useState(null);
    const everHadData = useRef({ reports: false, posts: false });
    const postsStatsLoaded = useRef(false);
    const size = 10;

    useEffect(() => {
      const t = setTimeout(() => { setDebouncedQ(q.trim()); setPage(0); }, 350);
      return () => clearTimeout(t);
    }, [q]);

    useEffect(() => { setPage(0); }, [tab, reportStatus, postsFilter]);

    useEffect(() => {
      if (tab !== "posts" || postsStatsLoaded.current || !forumService) return;
      postsStatsLoaded.current = true;
      Promise.all([
        forumService.getAdminForumPosts({ page: 0, size: 1 }),
        forumService.getAdminForumPosts({ includeDeleted: true, page: 0, size: 1 }),
      ]).then(([all, deleted]) => {
        const totalAll = deleted?.totalElements || 0;
        const totalActive = all?.totalElements || 0;
        setPostsSnapshot({
          total: totalActive,
          hidden: Math.max(0, totalAll - totalActive),
        });
      }).catch(() => {});
    }, [tab]);

    function load() {
      if (!forumService) return;
      setLoading(true);
      setError(null);
      const req = tab === "posts"
        ? (() => {
            const params = { keyword: debouncedQ || undefined, page, size };
            if (postsFilter === "hidden") params.includeDeleted = true;
            return forumService.getAdminForumPosts(params);
          })()
        : forumService.getAdminForumReports({ status: reportStatus !== "all" ? reportStatus : undefined, page, size });
      req.then(data => {
        everHadData.current[tab] = true;
        if (tab === "posts") {
          let items = data.content || [];
          if (postsFilter === "pinned") items = items.filter(p => p.pinned);
          else if (postsFilter === "hidden") items = items.filter(p => p.deleted);
          setPostsPage({ ...data, content: items });
          if (postsFilter === "all" && postsSnapshot && postsSnapshot.interactions === undefined) {
            setPostsSnapshot(prev => prev ? {
              ...prev,
              interactions: items.reduce((s, p) => s + (p.replyCount || 0) + (p.upvoteCount || 0), 0),
              pinned: items.filter(p => p.pinned).length,
            } : prev);
          }
        } else {
          setReportsPage(data);
          if (reportStatus === "all" && !snapshotStats) {
            const reports = data.content || [];
            setSnapshotStats({
              total: data.totalElements || 0,
              pending: reports.filter(r => r.status === "PENDING").length,
              resolved: reports.filter(r => r.status === "RESOLVED").length,
              dismissed: reports.filter(r => r.status === "DISMISSED").length,
            });
          }
        }
      }).catch(err => setError(err?.response?.data?.message || "Không thể tải dữ liệu forum"))
        .finally(() => setLoading(false));
    }

    useEffect(() => { load(); }, [tab, debouncedQ, reportStatus, postsFilter, page]);

    const stats = useMemo(() => {
      const posts = postsPage.content || [];
      return {
        total: snapshotStats?.total ?? 0,
        pending: snapshotStats?.pending ?? 0,
        resolved: snapshotStats?.resolved ?? 0,
        dismissed: snapshotStats?.dismissed ?? 0,
        totalPosts: postsSnapshot?.total ?? 0,
        interactions: postsSnapshot?.interactions ?? posts.reduce((s, p) => s + (p.replyCount || 0) + (p.upvoteCount || 0), 0),
        hiddenPosts: postsSnapshot?.hidden ?? 0,
        pinnedPosts: postsSnapshot?.pinned ?? posts.filter(p => p.pinned).length,
      };
    }, [snapshotStats, postsSnapshot, postsPage]);

    const pg = tab === "posts" ? makePg(postsPage, page, setPage, size) : makePg(reportsPage, page, setPage, size);

    async function handleDeletePost(post) {
      await forumService.deleteAdminForumPost(post.id);
      setConfirm(null);
      load();
    }

    async function handleReview(report, status, deleteTarget = false) {
      await forumService.reviewAdminForumReport(report.id, { status, deleteTarget });
      setConfirm(null);
      load();
    }

    return (
      <div className="page fade-in">
        <div className="page-head"><h1 className="t-h1">Quản lý Forum</h1><p>Kiểm duyệt bài viết và xử lý báo cáo nội dung vi phạm.</p></div>
        <div className="grid grid-stats" style={{ marginBottom: 22 }}>
          {tab === "reports" && <>
            <StatCard icon="list" iconBg="#eaf1ff" iconColor="#2563eb" value={stats.total.toLocaleString()} label="Tổng" />
            <StatCard icon="flag" iconBg="#fef5e6" iconColor="#d97706" value={stats.pending.toLocaleString()} label="Chờ xử lý" />
            <StatCard icon="check_circle" iconBg="#e7f8f0" iconColor="#059669" value={stats.resolved.toLocaleString()} label="Đã xử lý" />
            <StatCard icon="x" iconBg="#eef2f7" iconColor="#64748b" value={stats.dismissed.toLocaleString()} label="Bỏ qua" />
          </>}
          {tab === "posts" && <>
            <StatCard icon="message" iconBg="#eaf1ff" iconColor="#2563eb" value={stats.totalPosts.toLocaleString()} label="Tổng bài viết" />
            <StatCard icon="activity" iconBg="#f3edff" iconColor="#7c3aed" value={stats.interactions.toLocaleString()} label="Lượt tương tác" />
            <StatCard icon="eye_off" iconBg="#fdecec" iconColor="#dc2626" value={stats.hiddenPosts.toLocaleString()} label="Bài viết bị ẩn" />
            <StatCard icon="pin" iconBg="#e7f8f0" iconColor="#059669" value={stats.pinnedPosts.toLocaleString()} label="Bài viết ghim" />
          </>}
        </div>

        <div className="toolbar">
          <Tabs items={[{ v: "reports", label: "Báo cáo" }, { v: "posts", label: "Bài viết" }]} value={tab} onChange={setTab} />
          <div className="grow" />
          {tab === "reports" && <>
            <Select value={reportStatus} onChange={setReportStatus} options={[{ v: "all", label: "Tất cả" }, { v: "PENDING", label: "Chờ xử lý" }, { v: "RESOLVED", label: "Đã xử lý" }, { v: "DISMISSED", label: "Bỏ qua" }]} style={{ width: 150, flex: "none" }} />
          </>}
          {tab === "posts" && <>
            <Select value={postsFilter} onChange={setPostsFilter} options={[{ v: "all", label: "Tất cả" }, { v: "pinned", label: "Bài viết ghim" }, { v: "hidden", label: "Bài viết bị ẩn" }]} style={{ width: 160, flex: "none" }} />
            <Search placeholder="Tìm bài viết, tác giả..." value={q} onChange={setQ} style={{ width: 280, flex: "none" }} />
          </>}
        </div>

        <Section pad={false}>
          {!everHadData.current[tab] && loading && <div style={{ padding: 40, textAlign: "center", color: "var(--text-3)" }}>Đang tải dữ liệu...</div>}
          {error && <Empty icon="alert" title="Lỗi tải dữ liệu" text={error} />}
          {everHadData.current[tab] && !error && pg.slice.length === 0 && (
            <Empty
              icon={tab === "reports" ? "flag" : "message"}
              title={tab === "reports" ? "Không có báo cáo" : "Không có bài viết"}
              text={tab === "reports" ? "Không tìm thấy báo cáo phù hợp với bộ lọc hiện tại." : "Không tìm thấy bài viết phù hợp với bộ lọc hiện tại."}
            />
          )}
          {pg.slice.length > 0 && <div style={{ overflowX: "auto" }}>
            {tab === "reports" && <table className="tbl">
              <thead><tr><th>Nội dung bị báo cáo</th><th>Người báo cáo</th><th>Lý do</th><th>Ngày báo cáo</th><th>Trạng thái</th><th></th></tr></thead>
              <tbody>{pg.slice.map(r => (
                <tr key={r.id}>
                  <td style={{ minWidth: 260 }}><b className="truncate" style={{ display: "block", maxWidth: 360 }}>{r.targetTitle || "Nội dung"}</b></td>
                  <td><div className="row gap-9"><Avatar name={r.reporter?.fullName || "?"} src={r.reporter?.avatarUrl} size={30} /><span className="t-sm">{r.reporter?.fullName || "—"}</span></div></td>
                  <td><b className="t-sm">{reportReasonMap[r.reason] || r.reason}</b>{r.description && <div className="t-xs muted truncate" style={{ maxWidth: 220 }}>{r.description}</div>}</td>
                  <td className="muted t-sm">{fmtDate(r.createdAt)}</td>
                  <td>{statusChip(r.status)}</td>
                  <td><div className="row gap-6">{r.status === "PENDING" && <><button className="btn btn-soft btn-sm" onClick={() => handleReview(r, "DISMISSED")}>Bỏ qua</button><button className="btn btn-primary btn-sm" onClick={() => handleReview(r, "RESOLVED")}>Xử lý</button><button className="btn btn-danger btn-sm" disabled={r.targetDeleted} onClick={() => setConfirm({ type: "report-delete", report: r })}>Ẩn</button></>}<button className="icon-btn" title="Mở bài viết" onClick={() => { if (r.postId) window.open(`/admin/forum/posts?postId=${r.postId}`, "_blank"); }}><Ic n="eye" size={16} /></button></div></td>
                </tr>
              ))}</tbody>
            </table>}
            {tab === "posts" && <table className="tbl">
              <thead><tr><th style={{ width: "42%" }}>Bài viết</th><th style={{ width: "20%" }}>Tác giả</th><th style={{ width: "16%" }}>Tương tác</th><th style={{ width: "14%" }}>Ngày đăng</th><th style={{ width: "8%" }}></th></tr></thead>
              <tbody>{pg.slice.map(p => (
                <tr key={p.id}>
                  <td><b className="truncate" style={{ display: "block", maxWidth: 420 }}>{p.title}</b></td>
                  <td><div className="row gap-9"><Avatar name={p.author?.fullName || "?"} src={p.author?.avatarUrl} size={30} /><span className="t-sm truncate">{p.author?.fullName || "—"}</span></div></td>
                  <td><div className="row gap-4 t-sm" style={{ alignItems: "center" }}><Ic n="message" size={16} /><b style={{ marginRight: 10 }}>{p.replyCount}</b><Ic n="thumbs_up" size={16} /><b>{p.upvoteCount}</b></div></td>
                  <td className="muted t-sm">{fmtDate(p.createdAt)}</td>
                  <td><div className="row gap-6" style={{ justifyContent: "flex-end" }}><button className="icon-btn" title="Mở bài viết" onClick={() => window.open(`/admin/forum/posts?postId=${p.id}`, "_blank")}><Ic n="eye" size={16} /></button><button className="btn btn-danger btn-sm" disabled={p.deleted} onClick={() => setConfirm({ type: "post-delete", post: p })}>Ẩn</button></div></td>
                </tr>
              ))}</tbody>
            </table>}
          </div>}
        </Section>
        {!error && pg.total > 0 && <window.PageBar pg={pg} unit={tab === "reports" ? "báo cáo" : "bài viết"} forcePager />}

        <Modal open={!!confirm} onClose={() => setConfirm(null)} max={480}>
          {confirm && <>
            <ModalHead title="Xác nhận ẩn nội dung" sub="Nội dung sẽ không còn hiển thị trong forum." icon="warn" iconBg="#fff7ed" iconColor="#f97316" onClose={() => setConfirm(null)} />
            <div className="modal-foot"><button className="btn btn-ghost" onClick={() => setConfirm(null)}>Hủy</button><button className="btn btn-danger" onClick={() => confirm.type === "post-delete" ? handleDeletePost(confirm.post) : handleReview(confirm.report, "RESOLVED", true)}>Ẩn nội dung</button></div>
          </>}
        </Modal>
      </div>
    );
  }

  Object.assign(window, { AdminForum });
})();
