// @ts-nocheck
/* ============================================================
   RIKKEI EDU — Quản trị · Forum
   ============================================================ */
(function () {
  const { useEffect, useMemo, useState } = React;
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
    const [includeDeleted, setIncludeDeleted] = useState(false);
    const [postsPage, setPostsPage] = useState({ content: [], totalElements: 0, totalPages: 1 });
    const [reportsPage, setReportsPage] = useState({ content: [], totalElements: 0, totalPages: 1 });
    const [page, setPage] = useState(0);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState(null);
    const [confirm, setConfirm] = useState(null);
    const size = 10;

    useEffect(() => {
      const t = setTimeout(() => { setDebouncedQ(q.trim()); setPage(0); }, 350);
      return () => clearTimeout(t);
    }, [q]);

    useEffect(() => { setPage(0); }, [tab, reportStatus, includeDeleted]);

    function load() {
      if (!forumService) return;
      setLoading(true);
      setError(null);
      const req = tab === "posts"
        ? forumService.getAdminForumPosts({ keyword: debouncedQ || undefined, includeDeleted, page, size })
        : forumService.getAdminForumReports({ status: reportStatus !== "all" ? reportStatus : undefined, page, size });
      req.then(data => {
        if (tab === "posts") setPostsPage(data);
        else setReportsPage(data);
      }).catch(err => setError(err?.response?.data?.message || "Không thể tải dữ liệu forum"))
        .finally(() => setLoading(false));
    }

    useEffect(() => { load(); }, [tab, debouncedQ, reportStatus, includeDeleted, page]);

    const stats = useMemo(() => {
      const reports = reportsPage.content || [];
      return {
        pending: tab === "reports" && reportStatus === "PENDING" ? reportsPage.totalElements || 0 : reports.filter(r => r.status === "PENDING").length,
        reports: reportsPage.totalElements || 0,
        posts: postsPage.totalElements || 0,
      };
    }, [reportsPage, postsPage, tab, reportStatus]);

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
          <StatCard icon="flag" iconBg="#fef5e6" iconColor="#d97706" value={loading ? "..." : stats.pending.toLocaleString()} label="Báo cáo chờ xử lý" />
          <StatCard icon="shield" iconBg="#eaf1ff" iconColor="#2563eb" value={loading ? "..." : stats.reports.toLocaleString()} label="Báo cáo đang xem" />
          <StatCard icon="message" iconBg="#f3edff" iconColor="#7c3aed" value={loading ? "..." : stats.posts.toLocaleString()} label="Bài viết đang xem" />
        </div>

        <div className="toolbar">
          <Tabs items={[{ v: "reports", label: "Báo cáo" }, { v: "posts", label: "Bài viết" }]} value={tab} onChange={setTab} />
          <div className="grow" />
          {tab === "reports" ? <>
            <Select value={reportStatus} onChange={setReportStatus} options={[{ v: "all", label: "Tất cả" }, { v: "PENDING", label: "Chờ xử lý" }, { v: "RESOLVED", label: "Đã xử lý" }, { v: "DISMISSED", label: "Bỏ qua" }]} style={{ width: 150, flex: "none" }} />
          </> : <>
            <label className="row gap-8 t-sm" style={{ flex: "none" }}><input type="checkbox" checked={includeDeleted} onChange={e => setIncludeDeleted(e.target.checked)} />Gồm đã ẩn</label>
            <Search placeholder="Tìm bài viết, tác giả..." value={q} onChange={setQ} style={{ width: 280, flex: "none" }} />
          </>}
        </div>

        <Section pad={false}>
          {loading && <div style={{ padding: 40, textAlign: "center", color: "var(--text-3)" }}>Đang tải dữ liệu...</div>}
          {!loading && error && <Empty icon="alert" title="Lỗi tải dữ liệu" text={error} />}
          {!loading && !error && pg.slice.length === 0 && (
            <Empty
              icon={tab === "reports" ? "flag" : "message"}
              title={tab === "reports" ? "Không có báo cáo" : "Không có bài viết"}
              text={tab === "reports" ? "Không tìm thấy báo cáo phù hợp với bộ lọc hiện tại." : "Không tìm thấy bài viết phù hợp với bộ lọc hiện tại."}
            />
          )}
          {!loading && !error && pg.slice.length > 0 && <div style={{ overflowX: "auto" }}>
            {tab === "reports" ? <table className="tbl">
              <thead><tr><th>Nội dung bị báo cáo</th><th>Người báo cáo</th><th>Lý do</th><th>Ngày báo cáo</th><th>Trạng thái</th><th></th></tr></thead>
              <tbody>{pg.slice.map(r => (
                <tr key={r.id}>
                  <td style={{ minWidth: 280 }}><div className="row gap-8 wrap" style={{ marginBottom: 5 }}><span className="chip chip-neutral">{r.targetType === "POST" ? "Bài viết" : "Bình luận"}</span>{r.targetDeleted && <span className="chip chip-error">Đã ẩn</span>}{r.courseTitle && <span className="chip chip-info">{r.courseTitle}</span>}</div><b className="truncate" style={{ display: "block", maxWidth: 360 }}>{r.targetTitle || "Nội dung"}</b><div className="t-xs muted truncate" style={{ maxWidth: 420, marginTop: 4 }}>{r.targetContentPreview}</div></td>
                  <td><div className="row gap-9"><Avatar name={r.reporter?.fullName || "?"} src={r.reporter?.avatarUrl} size={30} /><span className="t-sm">{r.reporter?.fullName || "—"}</span></div></td>
                  <td><b className="t-sm">{reportReasonMap[r.reason] || r.reason}</b>{r.description && <div className="t-xs muted truncate" style={{ maxWidth: 220 }}>{r.description}</div>}</td>
                  <td className="muted t-sm">{fmtDate(r.createdAt)}</td>
                  <td>{statusChip(r.status)}</td>
                  <td><div className="row gap-6">{r.status === "PENDING" && <><button className="btn btn-soft btn-sm" onClick={() => handleReview(r, "DISMISSED")}>Bỏ qua</button><button className="btn btn-primary btn-sm" onClick={() => handleReview(r, "RESOLVED")}>Xử lý</button><button className="btn btn-danger btn-sm" disabled={r.targetDeleted} onClick={() => setConfirm({ type: "report-delete", report: r })}>Ẩn</button></>}<button className="icon-btn" title="Mở bài viết" onClick={() => { if (r.postId) window.open(`/admin/forum/posts?postId=${r.postId}`, "_blank"); }}><Ic n="eye" size={16} /></button></div></td>
                </tr>
              ))}</tbody>
            </table> : <table className="tbl">
              <thead><tr><th>Bài viết</th><th>Tác giả</th><th>Tương tác</th><th>Báo cáo</th><th>Ngày đăng</th><th></th></tr></thead>
              <tbody>{pg.slice.map(p => (
                <tr key={p.id}>
                  <td style={{ minWidth: 300 }}><div className="row gap-8 wrap" style={{ marginBottom: 5 }}>{p.deleted && <span className="chip chip-error">Đã ẩn</span>}{p.pinned && <span className="chip chip-warning">Ghim</span>}<span className="chip chip-neutral">{topicLabel(p.topic)}</span><span className="chip chip-info">{p.courseTitle}</span></div><b className="truncate" style={{ display: "block", maxWidth: 420 }}>{p.title}</b><div className="t-xs muted truncate" style={{ maxWidth: 460, marginTop: 4 }}>{p.contentPreview}</div></td>
                  <td><div className="row gap-9"><Avatar name={p.author?.fullName || "?"} src={p.author?.avatarUrl} size={30} /><span className="t-sm">{p.author?.fullName || "—"}</span></div></td>
                  <td className="t-sm"><b>{p.replyCount}</b> trả lời<br /><b>{p.upvoteCount}</b> thích</td>
                  <td><span className={p.pendingReportCount > 0 ? "chip chip-warning" : "chip chip-neutral"}>{p.pendingReportCount}/{p.reportCount}</span></td>
                  <td className="muted t-sm">{fmtDate(p.createdAt)}</td>
                  <td><div className="row gap-6"><button className="icon-btn" title="Mở bài viết" onClick={() => window.open(`/admin/forum/posts?postId=${p.id}`, "_blank")}><Ic n="eye" size={16} /></button><button className="btn btn-danger btn-sm" disabled={p.deleted} onClick={() => setConfirm({ type: "post-delete", post: p })}>Ẩn</button></div></td>
                </tr>
              ))}</tbody>
            </table>}
          </div>}
        </Section>
        {!loading && !error && pg.total > 0 && <window.PageBar pg={pg} unit={tab === "reports" ? "báo cáo" : "bài viết"} forcePager />}

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
