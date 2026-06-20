// @ts-nocheck
/* ============================================================
   RIKKEI EDU — Quản trị · Phê duyệt Khóa học (+ popup chi tiết / xem trước / từ chối)
   ============================================================ */
(function () {
  const { useState, useEffect } = React;
  const Ic = window.Icon;
  const { Avatar, Status, StatCard, Search, Tabs, Select, Section, Modal, ModalHead } = window;
  const http = window.httpClient;

  const actIcon = { approve:"check_circle", submit:"upload", cert:"award", group:"layers", ban:"lock", grade:"edit", warn:"shield", add:"plus", reject:"x", publish:"book" };
  const actColor = { approve:"#10b981", submit:"#2563eb", cert:"#10b981", group:"#7c3aed", ban:"#ef4444", grade:"#2563eb", warn:"#f59e0b", add:"#10b981", reject:"#ef4444", publish:"#2563eb" };

  function fmtDate(d) {
    if (!d) return "-";
    return new Date(d).toLocaleDateString("vi-VN");
  }

  function mapStatus(s) {
    if (!s) return "pending";
    if (s === "PENDING_APPROVAL") return "pending";
    if (s === "APPROVED") return "approved";
    if (s === "REJECTED") return "rejected";
    return "pending";
  }

  function countLessons(course) {
    if (!course?.chapters) return course?.lessonCount || 0;
    return course.chapters.reduce((s, ch) => s + (ch.lessons?.length || 0), 0);
  }

  /* ---------------- Detail modal content ---------------- */
  function DetailModal({ detail, onClose, onApprove, onReject, onPreview }) {
    const [imgFull, setImgFull] = useState(null);
    const allLessons = (detail.chapters || []).flatMap(ch => ch.lessons || []);
    const allResources = allLessons.flatMap(l => l.resources || []);
    const isPending = mapStatus(detail.status) === "pending";
    const typeColor = { PDF: "#ef4444", DOC: "#2563eb", SLIDE: "#f59e0b", IMAGE: "#10b981" };
    const typeBg   = { PDF: "#fdecec", DOC: "#eaf1ff", SLIDE: "#fef5e6", IMAGE: "#e7f8f0" };

    return <>
      {/* Banner */}
      <div style={{ height: 190, background: "#0f172a", borderRadius: "18px 18px 0 0", position: "relative" }}>
        <button className="icon-btn" style={{ position: "absolute", right: 14, top: 14, width: 36, height: 36, background: "rgba(15,23,42,.6)", color: "#fff", border: "none" }} onClick={onClose}>
          <Ic n="x" size={18} />
        </button>
        <span style={{ position: "absolute", left: 16, bottom: 14 }}>
          <span className="chip" style={{ background: "rgba(15,23,42,.72)", color: "#fff" }}>{detail.category?.name || "-"}</span>
        </span>
        {(() => {
          const url = detail.thumbnailUrl || "";
          const ext = url.split(".").pop()?.toLowerCase().split("?")[0] || "";
          const isImg   = ["jpg","jpeg","png","gif","webp","svg"].includes(ext);
          const isVideo = ["mp4","webm","mov","avi"].includes(ext);
          const isSlide = ["ppt","pptx"].includes(ext);

          if (isImg) {
            return (
              <img src={url} alt="" onClick={() => setImgFull(url)}
                style={{ position: "absolute", inset: 0, width: "100%", height: "100%", objectFit: "cover", opacity: 0.8, cursor: "zoom-in" }} />
            );
          }
          if (isVideo) {
            return (
              <video src={url} muted style={{ position: "absolute", inset: 0, width: "100%", height: "100%", objectFit: "cover", opacity: 0.7 }} />
            );
          }
          if (isSlide) {
            return (
              <div style={{ position: "absolute", inset: 0, display: "flex", alignItems: "center", justifyContent: "center", flexDirection: "column", gap: 6 }}>
                <div style={{ width: 52, height: 52, borderRadius: 12, background: "#fef5e6", display: "flex", alignItems: "center", justifyContent: "center" }}>
                  <Ic n="layers" size={26} style={{ color: "#f59e0b" }} />
                </div>
                <span style={{ color: "#94a3b8", fontSize: 12 }}>Trình chiếu</span>
              </div>
            );
          }
          // fallback — play button
          return (
            <div style={{ position: "absolute", inset: 0, display: "grid", placeItems: "center" }}>
              <button onClick={onPreview}
                style={{ width: 64, height: 64, borderRadius: 999, border: "none", background: "rgba(255,255,255,.92)", color: "var(--primary)", display: "grid", placeItems: "center", cursor: "pointer", boxShadow: "0 8px 24px rgba(0,0,0,.3)" }}>
                <Ic n="play" size={26} fill="currentColor" />
              </button>
            </div>
          );
        })()}
        <button onClick={onPreview}
          style={{ position: "absolute", right: 16, bottom: 14, background: "rgba(15,23,42,.72)", color: "#fff", border: "none", borderRadius: 999, padding: "4px 10px", fontSize: 12, display: "flex", alignItems: "center", gap: 5, cursor: "pointer" }}>
          <Ic n="eye" size={13} />Xem trước
        </button>
      </div>

      {/* Body */}
      <div className="modal-body" style={{ overflowY: "auto", flex: 1 }}>
        <h2 className="t-h2" style={{ margin: "0 0 8px" }}>{detail.title}</h2>
        <div className="row gap-10" style={{ marginBottom: 16 }}>
          <Avatar name={detail.instructorName || "?"} size={34} />
          <div>
            <div style={{ fontWeight: 600, fontSize: 14 }}>{detail.instructorName}</div>
            <div className="t-xs muted">Gửi ngày {fmtDate(detail.submittedAt || detail.createdAt)}</div>
          </div>
        </div>

        {/* Stats */}
        <div className="grid grid-2" style={{ gap: 12, marginBottom: 18 }}>
          {[
            { l: "Số bài giảng", v: allLessons.length, ic: "book" },
            { l: "Số chương",    v: detail.chapters?.length || 0, ic: "layers" },
            { l: "Danh mục",     v: detail.category?.name || "-", ic: "folder" },
            { l: "Tài liệu",     v: allResources.length + " tệp", ic: "paperclip" },
          ].map((s, i) => (
            <div key={i} className="row gap-10" style={{ padding: 12, background: "var(--surface-2)", borderRadius: 11 }}>
              <div className="stat-ic" style={{ width: 38, height: 38, borderRadius: 10, background: "#fff", color: "var(--accent)" }}>
                <Ic n={s.ic} size={18} />
              </div>
              <div>
                <div className="t-xs muted">{s.l}</div>
                <div style={{ fontWeight: 700, fontSize: 14 }}>{s.v}</div>
              </div>
            </div>
          ))}
        </div>

        {/* Chapters & Lessons */}
        {(detail.chapters || []).map((ch, ci) => (
          <div key={ch.id || ci} style={{ marginBottom: 18 }}>
            <div className="between" style={{ marginBottom: 9 }}>
              <div className="t-label" style={{ margin: 0 }}>Chương {ci + 1}: {ch.title}</div>
              <span className="t-xs dim">{ch.lessons?.length || 0} bài</span>
            </div>
            <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
              {(ch.lessons || []).map((l, li) => {
                const isVideo = l.lessonType === "VIDEO";
                return (
                  <div key={l.id || li} className="row gap-11" style={{ padding: 10, border: "1px solid var(--border)", borderRadius: 11 }}>
                    <div style={{ width: 56, height: 38, borderRadius: 8, flex: "none", background: "#0f172a", display: "grid", placeItems: "center" }}>
                      <Ic n={isVideo ? "play" : "file-text"} size={15} fill={isVideo ? "#fff" : undefined} style={{ color: "#fff" }} />
                    </div>
                    <div className="grow" style={{ minWidth: 0 }}>
                      <div style={{ fontWeight: 600, fontSize: 13.5 }} className="truncate">{l.title}</div>
                      <div className="t-xs muted row gap-5" style={{ marginTop: 2 }}>
                        <Ic n={isVideo ? "video" : "file-text"} size={12} />
                        {isVideo ? "Video" : "Văn bản"}
                        {l.resources?.length > 0 && <> • <Ic n="paperclip" size={12} />{l.resources.length} tài liệu</>}
                      </div>
                    </div>
                    <button className="btn btn-soft btn-sm" style={{ flex: "none" }} onClick={onPreview}>
                      <Ic n="eye" size={14} />Xem
                    </button>
                  </div>
                );
              })}
            </div>
          </div>
        ))}

        {/* Resources */}
        {allResources.length > 0 && <>
          <div className="between" style={{ marginBottom: 9 }}>
            <div className="t-label" style={{ margin: 0 }}>Tài liệu đính kèm</div>
            <span className="t-xs dim">{allResources.length} tệp</span>
          </div>
          <div style={{ display: "flex", flexDirection: "column", gap: 8, marginBottom: 18 }}>
            {allResources.map((r, i) => (
              <div key={r.id || i} className="row gap-11" style={{ padding: 10, border: "1px solid var(--border)", borderRadius: 11 }}>
                <div className="stat-ic" style={{ width: 40, height: 40, borderRadius: 10, flex: "none", background: typeBg[r.resourceType] || "var(--surface-2)", color: typeColor[r.resourceType] || "#64748b" }}>
                  <Ic n="file" size={19} />
                </div>
                <div className="grow" style={{ minWidth: 0 }}>
                  <div style={{ fontWeight: 600, fontSize: 13.5 }} className="truncate">{r.displayName || r.originalFilename}</div>
                  <div className="t-xs muted">{r.resourceType}</div>
                </div>
              </div>
            ))}
          </div>
        </>}

        {/* Description */}
        {detail.description && <>
          <div className="t-label" style={{ marginBottom: 7 }}>Mô tả khóa học</div>
          <p className="muted t-sm" style={{ margin: 0, lineHeight: 1.6 }}>{detail.description}</p>
        </>}
      </div>

      {/* Footer */}
      {isPending && (
        <div className="modal-foot">
          <button className="btn btn-danger" onClick={onReject}><Ic n="x" size={16} />Từ chối</button>
          <button className="btn btn-success" onClick={onApprove}><Ic n="check" size={16} />Phê duyệt & Xuất bản</button>
        </div>
      )}

      {/* Lightbox xem ảnh full */}
      {imgFull && (
        <div onClick={() => setImgFull(null)}
          style={{ position: "fixed", inset: 0, zIndex: 400, background: "rgba(0,0,0,.88)", display: "flex", alignItems: "center", justifyContent: "center", cursor: "zoom-out" }}>
          <img src={imgFull} alt="" style={{ maxWidth: "90vw", maxHeight: "90vh", borderRadius: 12, boxShadow: "0 24px 64px rgba(0,0,0,.6)", objectFit: "contain" }} />
          <button onClick={() => setImgFull(null)}
            style={{ position: "absolute", top: 16, right: 16, width: 40, height: 40, borderRadius: 999, border: "none", background: "rgba(255,255,255,.15)", color: "#fff", cursor: "pointer", display: "grid", placeItems: "center" }}>
            <Ic n="x" size={20} />
          </button>
        </div>
      )}
    </>;
  }

  /* ---------------- Course approval ---------------- */
  function AdminApproval({ nav }) {
    const [tab, setTab] = useState("pending");
    const [list, setList] = useState([]);
    const [loading, setLoading] = useState(false);
    const [stats, setStats] = useState({ pending: 0, approved: 0, rejected: 0 });
    const [page, setPage] = useState(0);
    const [totalPages, setTotalPages] = useState(1);
    const [qSearch, setQSearch] = useState("");

    const [detail, setDetail] = useState(null);
    const [detailLoading, setDetailLoading] = useState(false);

    const [reject, setReject] = useState(null);
    const [rejectSelect, setRejectSelect] = useState("Nội dung chưa đầy đủ");
    const [rejectNote, setRejectNote] = useState("");
    const [rejectLoading, setRejectLoading] = useState(false);
    const [showPreview, setShowPreview] = useState(false);

    useEffect(() => { loadList(); }, [tab, page]);

    async function loadList() {
      setLoading(true);
      try {
        let url;
        if (tab === "pending") {
          url = `/admin/courses/pending?page=${page}&size=10`;
        } else {
          const statusMap = { approved: "APPROVED", rejected: "REJECTED" };
          const s = statusMap[tab];
          url = `/admin/courses?page=${page}&size=10${s ? `&status=${s}` : ""}`;
        }
        const res = await http.get(url);
        const data = res.data;
        const items = data.content || data || [];
        setList(items);
        setTotalPages(data.totalPages || 1);
        if (tab === "pending") setStats(st => ({ ...st, pending: data.totalElements || items.length }));
        if (tab === "approved") setStats(st => ({ ...st, approved: data.totalElements || items.length }));
        if (tab === "rejected") setStats(st => ({ ...st, rejected: data.totalElements || items.length }));
      } catch (e) {
        console.error(e);
      } finally {
        setLoading(false);
      }
    }

    async function openDetail(item) {
      setDetail({ ...item, _loading: true });
      try {
        const res = await http.get(`/admin/courses/${item.id}`);
        setDetail(res.data);
      } catch (e) {
        console.error(e);
        setDetail(null);
      }
    }

    async function handleApprove(id) {
      try {
        await http.post(`/admin/courses/${id}/approve`);
        setList(l => l.filter(c => c.id !== id));
        setStats(st => ({ ...st, pending: Math.max(0, st.pending - 1), approved: st.approved + 1 }));
      } catch (e) {
        alert("Lỗi khi phê duyệt: " + (e?.response?.data?.message || e.message));
      }
    }

    async function handleReject() {
      const reason = rejectNote.trim() || rejectSelect;
      if (!reason) return;
      setRejectLoading(true);
      try {
        await http.post(`/admin/courses/${reject.id}/reject`, { reason });
        setList(l => l.filter(c => c.id !== reject.id));
        setStats(st => ({ ...st, pending: Math.max(0, st.pending - 1), rejected: st.rejected + 1 }));
        setReject(null);
        setRejectNote("");
      } catch (e) {
        alert("Lỗi khi từ chối: " + (e?.response?.data?.message || e.message));
      } finally {
        setRejectLoading(false);
      }
    }

    let filtered = list;
    if (qSearch) filtered = list.filter(a =>
      (a.title || "").toLowerCase().includes(qSearch.toLowerCase()) ||
      (a.instructorName || "").toLowerCase().includes(qSearch.toLowerCase())
    );

    return (
      <div className="page fade-in">
        <div className="page-head"><h1 className="t-h1">Phê duyệt Khóa học</h1><p>Kiểm duyệt nội dung khóa học do giảng viên gửi trước khi xuất bản.</p></div>
        <div className="grid grid-stats" style={{ marginBottom: 22, gridTemplateColumns: "repeat(3,1fr)" }}>
          <StatCard icon="clipboard" iconBg="#fef5e6" iconColor="#d97706" value={String(stats.pending)} label="Đang chờ duyệt" />
          <StatCard icon="check_circle" iconBg="#e7f8f0" iconColor="#059669" value={String(stats.approved)} label="Đã duyệt" />
          <StatCard icon="x" iconBg="#fdecec" iconColor="#dc2626" value={String(stats.rejected)} label="Đã từ chối" />
        </div>
        <div className="toolbar">
          <Tabs items={[{v:"pending",label:"Chờ duyệt"},{v:"approved",label:"Đã duyệt"},{v:"rejected",label:"Từ chối"},{v:"all",label:"Tất cả"}]} value={tab} onChange={v => { setTab(v); setPage(0); }} />
          <div className="grow" />
          <Search placeholder="Tìm khóa học, giảng viên..." value={qSearch} onChange={setQSearch} style={{ width: 280, flex: "none" }} />
        </div>
        <Section pad={false}>
          <div style={{ overflowX: "auto" }}><table className="tbl">
            <thead><tr><th>Khóa học</th><th>Giảng viên</th><th>Danh mục</th><th>Số bài</th><th>Ngày gửi</th><th>Trạng thái</th><th></th></tr></thead>
            <tbody>{loading ? (
              <tr><td colSpan={7} style={{ textAlign: "center", padding: 32, color: "var(--text-3)" }}>Đang tải...</td></tr>
            ) : filtered.length === 0 ? (
              <tr><td colSpan={7} style={{ textAlign: "center", padding: 32, color: "var(--text-3)" }}>Không có dữ liệu</td></tr>
            ) : filtered.map(a => {
              const st = mapStatus(a.status);
              return (
                <tr key={a.id}>
                  <td><b style={{ fontSize: 14, maxWidth: 240, display: "block" }} className="truncate">{a.title}</b></td>
                  <td><div className="row gap-9"><Avatar name={a.instructorName || "?"} size={30} /><span className="t-sm">{a.instructorName}</span></div></td>
                  <td><span className="chip chip-neutral">{a.category?.name || "-"}</span></td>
                  <td><b>{a.lessonCount ?? countLessons(a)}</b></td>
                  <td className="muted">{fmtDate(a.submittedAt || a.createdAt)}</td>
                  <td><Status s={st} /></td>
                  <td>{st === "pending"
                    ? <div className="row gap-6">
                        <button className="btn btn-soft btn-sm" onClick={() => openDetail(a)}>Xem</button>
                        <button className="btn btn-success btn-sm" onClick={() => handleApprove(a.id)}><Ic n="check" size={15} /></button>
                        <button className="btn btn-danger btn-sm" onClick={() => { setReject(a); setRejectNote(""); setRejectSelect("Nội dung chưa đầy đủ"); }}><Ic n="x" size={15} /></button>
                      </div>
                    : <button className="btn btn-ghost btn-sm" onClick={() => openDetail(a)}>Chi tiết</button>
                  }</td>
                </tr>
              );
            })}</tbody>
          </table></div>
        </Section>
        {totalPages > 1 && (
          <div className="row gap-8" style={{ justifyContent: "center", marginTop: 16 }}>
            <button className="btn btn-ghost btn-sm" disabled={page === 0} onClick={() => setPage(p => p - 1)}>‹ Trước</button>
            <span className="t-sm muted">Trang {page + 1} / {totalPages}</span>
            <button className="btn btn-ghost btn-sm" disabled={page >= totalPages - 1} onClick={() => setPage(p => p + 1)}>Sau ›</button>
          </div>
        )}

        {/* Detail modal */}
        <Modal open={!!detail} onClose={() => setDetail(null)} max={620} maxHeight="calc(100vh - 48px)">
          {detail && (detail._loading
            ? <div style={{ padding: 64, textAlign: "center", color: "var(--text-3)" }}>Đang tải...</div>
            : <DetailModal
                detail={detail}
                onClose={() => setDetail(null)}
                onApprove={() => { handleApprove(detail.id); setDetail(null); }}
                onReject={() => { setReject(detail); setDetail(null); setRejectNote(""); setRejectSelect("Nội dung chưa đầy đủ"); }}
                onPreview={() => { setDetail(null); window.__previewCourse = { courseId: detail.id, role: "admin" }; setShowPreview(true); }}
              />
          )}
        </Modal>

        {/* Reject modal */}
        <Modal open={!!reject} onClose={() => setReject(null)} max={480}>
          {reject && <>
            <ModalHead title="Từ chối khóa học" sub={reject.title || reject.course} icon="warn" iconBg="#fdecec" iconColor="#dc2626" onClose={() => setReject(null)} />
            <div className="modal-body">
              <div className="t-label" style={{ marginBottom: 7 }}>Lý do từ chối</div>
              <Select value={rejectSelect} onChange={setRejectSelect} options={[
                { v: "Nội dung chưa đầy đủ", label: "Nội dung chưa đầy đủ" },
                { v: "Vi phạm bản quyền", label: "Vi phạm bản quyền" },
                { v: "Chất lượng video kém", label: "Chất lượng video kém" },
                { v: "Lý do khác", label: "Lý do khác" },
              ]} />
              <div className="t-label" style={{ margin: "16px 0 7px" }}>Ghi chú cho giảng viên</div>
              <textarea className="input" style={{ height: 100, padding: 12, resize: "none" }}
                placeholder="Mô tả chi tiết để giảng viên chỉnh sửa..."
                value={rejectNote}
                onChange={e => setRejectNote(e.target.value)}
              />
            </div>
            <div className="modal-foot">
              <button className="btn btn-ghost" onClick={() => setReject(null)}>Hủy</button>
              <button className="btn btn-primary" style={{ background: "var(--error)" }} disabled={rejectLoading} onClick={handleReject}>
                {rejectLoading ? "Đang gửi..." : "Xác nhận từ chối"}
              </button>
            </div>
          </>}
        </Modal>
        {showPreview && React.createElement(window.PreviewPlayer, { onBack: () => setShowPreview(false) })}
      </div>
    );
  }

  Object.assign(window, { AdminApproval });
})();
