// @ts-nocheck
(function () {
  const { useState } = React;
  const Ic  = window.Icon;
  const { Section, Empty } = window;

  function fmtDT(iso: string) {
    if (!iso) return "—";
    return new Date(iso).toLocaleString("vi-VN", { dateStyle: "short", timeStyle: "short" });
  }

  const STATUS_CFG = {
    DRAFT:    { label: "Bản nháp",       color: "#64748b", bg: "#f1f5f9", border: "#cbd5e1" },
    PENDING:  { label: "Đang chờ duyệt", color: "#0284c7", bg: "#e0f2fe", border: "#7dd3fc" },
    APPROVED: { label: "Đã duyệt",       color: "#16a34a", bg: "#dcfce7", border: "#86efac" },
    REJECTED: { label: "Bị từ chối",     color: "#dc2626", bg: "#fee2e2", border: "#fca5a5" },
  };

  /**
   * Tab "Phiên bản".
   * Props: versions, course, hasPendingChanges, historyLoading, savingDraft,
   *        rollingBack, deletingDraft, renamingVersion, renameInput,
   *        setRenamingVersion, setRenameInput, setSnapshotView,
   *        setShowSaveDraft, handleRenameVersion, handleDeleteDraft,
   *        handleRollback, submittingVersion, handleSubmitVersion
   */
  function CourseVersionsTab({
    versions, course, hasPendingChanges, historyLoading,
    savingDraft, rollingBack, deletingDraft,
    renamingVersion, renameInput, setRenamingVersion, setRenameInput,
    setSnapshotView, setShowSaveDraft,
    handleRenameVersion, handleDeleteDraft, handleRollback,
    handleWithdraw,
    submittingVersion, handleSubmitVersion,
  }: any) {
    const draftVersions    = versions.filter(v => v.status === "DRAFT");
    const officialVersions = versions.filter(v => v.status !== "DRAFT");
    const latestApprovedNo = officialVersions.filter(v => v.status === "APPROVED")[0]?.versionNumber;
    const canRollback      = course?.status === "PUBLISHED";
    const draftCount       = draftVersions.length;

    function VersionCard({ v }: any) {
      const cfg     = STATUS_CFG[v.status] || STATUS_CFG.PENDING;
      const isLive  = v.status === "APPROVED" && v.versionNumber === latestApprovedNo;
      const isDraft = v.status === "DRAFT";
      const snap    = (() => { try { return v.snapshot ? JSON.parse(v.snapshot) : null; } catch { return null; } })();

      return (
        <div style={{ borderRadius: 12, overflow: "hidden", border: `1.5px solid ${cfg.border}`, background: "var(--surface)", boxShadow: v.status === "REJECTED" ? "0 2px 8px rgba(220,38,38,.08)" : "none" }}>
          {/* Header */}
          <div style={{ padding: "10px 16px", background: cfg.bg, display: "flex", alignItems: "center", gap: 10 }}>
            <div style={{ width: 28, height: 28, borderRadius: 8, background: cfg.color, color: "#fff", display: "grid", placeItems: "center", fontSize: 12, fontWeight: 800, flex: "none" }}>
              {isDraft ? <Ic n="file" size={13} /> : `v${v.versionNumber}`}
            </div>

            <div style={{ flex: 1, minWidth: 0 }}>
              {isDraft && renamingVersion === v.id ? (
                <form style={{ display: "inline-flex", gap: 6, alignItems: "center" }}
                  onSubmit={e => { e.preventDefault(); handleRenameVersion(v.id, renameInput); }}>
                  <input autoFocus value={renameInput} onChange={e => setRenameInput(e.target.value)}
                    style={{ fontSize: 13, padding: "2px 8px", borderRadius: 6, border: "1.5px solid #93c5fd", outline: "none", minWidth: 160, maxWidth: 260 }}
                    onKeyDown={e => e.key === "Escape" && setRenamingVersion(null)} />
                  <button type="submit" className="btn btn-ghost btn-sm" style={{ fontSize: 12, padding: "2px 8px", color: "#1d4ed8" }}>Lưu</button>
                  <button type="button" className="btn btn-ghost btn-sm" style={{ fontSize: 12, padding: "2px 8px" }} onClick={() => setRenamingVersion(null)}>Hủy</button>
                </form>
              ) : (
                <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
                  <span style={{ fontWeight: 700, fontSize: 13.5, color: cfg.color }}>
                    {isDraft ? (v.label || "Bản nháp chưa đặt tên") : `Phiên bản ${v.versionNumber}`}
                  </span>
                  {isDraft && (
                    <button className="btn btn-ghost btn-sm" title="Đổi tên"
                      style={{ padding: "2px 6px", fontSize: 11, color: "#64748b", borderColor: "#e2e8f0", gap: 3 }}
                      onClick={() => { setRenamingVersion(v.id); setRenameInput(v.label || ""); }}>
                      <Ic n="edit" size={11} />đổi tên
                    </button>
                  )}
                  {isLive && (
                    <span style={{ padding: "2px 8px", borderRadius: 999, background: "#16a34a", color: "#fff", fontSize: 10.5, fontWeight: 700 }}>ĐANG LIVE</span>
                  )}
                </div>
              )}
              <span className="muted" style={{ fontSize: 12 }}>
                · {isDraft ? "Lưu" : "Nộp"} {fmtDT(v.submittedAt)}
              </span>
            </div>

            <span style={{ padding: "3px 10px", borderRadius: 999, fontSize: 11.5, fontWeight: 700, background: cfg.color, color: "#fff", flex: "none" }}>{cfg.label}</span>
          </div>

          {/* Body */}
          <div style={{ padding: "12px 16px", display: "flex", flexDirection: "column", gap: 10 }}>
            {!isDraft && (
              <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
                <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                  <div style={{ width: 8, height: 8, borderRadius: "50%", background: "#0284c7", flex: "none" }} />
                  <span style={{ fontSize: 12.5, color: "var(--text-2)" }}>Nộp lúc <strong>{fmtDT(v.submittedAt)}</strong></span>
                </div>
                {v.reviewedAt && (
                  <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                    <div style={{ width: 8, height: 8, borderRadius: "50%", background: cfg.color, flex: "none" }} />
                    <span style={{ fontSize: 12.5, color: "var(--text-2)" }}>
                      {v.status === "APPROVED" ? "Duyệt" : "Từ chối"} lúc <strong>{fmtDT(v.reviewedAt)}</strong>
                    </span>
                  </div>
                )}
                {v.status === "PENDING" && (
                  <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                    <div style={{ width: 8, height: 8, borderRadius: "50%", background: "#fbbf24", flex: "none", boxShadow: "0 0 0 3px #fef3c7" }} />
                    <span style={{ fontSize: 12.5, color: "#a16207", fontStyle: "italic" }}>Đang chờ admin xét duyệt...</span>
                  </div>
                )}
                {v.status === "APPROVED" && isLive && (
                  <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                    <div style={{ width: 8, height: 8, borderRadius: "50%", background: "#16a34a", flex: "none", boxShadow: "0 0 0 3px #dcfce7" }} />
                    <span style={{ fontSize: 12.5, color: "#15803d", fontWeight: 600 }}>Đang là phiên bản live</span>
                  </div>
                )}
              </div>
            )}

            {v.changeSummary && (
              <div style={{ fontSize: 12.5, color: "var(--text-2)", fontStyle: "italic" }}>Mô tả: {v.changeSummary}</div>
            )}

            {v.status === "REJECTED" && v.rejectionReason && (
              <div style={{ padding: "10px 14px", borderRadius: 9, background: "#fff5f5", border: "1px solid #fecaca", display: "flex", gap: 10, alignItems: "flex-start" }}>
                <Ic n="warn" size={15} style={{ color: "#dc2626", flex: "none", marginTop: 1 }} />
                <div>
                  <div style={{ fontSize: 10.5, fontWeight: 700, color: "#dc2626", marginBottom: 3, textTransform: "uppercase" }}>Lý do từ chối</div>
                  <div style={{ fontSize: 13, color: "#991b1b", lineHeight: 1.5 }}>{v.rejectionReason}</div>
                </div>
              </div>
            )}

            {/* Actions */}
            <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
              {snap && (
                <button className="btn btn-ghost btn-sm" style={{ fontSize: 12.5, gap: 6 }}
                  onClick={() => setSnapshotView({ versionNo: isDraft ? (v.label || "Bản nháp") : v.versionNumber, snapshot: snap })}>
                  <Ic n="eye" size={13} />Xem nội dung
                </button>
              )}
              {isDraft && (
                <button className="btn btn-ghost btn-sm"
                  style={{ fontSize: 12.5, gap: 6, color: "#0284c7", borderColor: "#7dd3fc" }}
                  disabled={submittingVersion}
                  onClick={() => handleSubmitVersion(v.id, v.label || "Bản nháp")}>
                  <Ic n="send" size={13} />
                  {submittingVersion ? "Đang nộp..." : "Nộp bản nháp này"}
                </button>
              )}
              {v.status === "PENDING" && handleWithdraw && (
                <button className="btn btn-ghost btn-sm"
                  style={{ fontSize: 12.5, gap: 6, color: "#d97706", borderColor: "#fde68a" }}
                  onClick={handleWithdraw}>
                  <Ic n="undo" size={13} />
                  Rút gửi duyệt
                </button>
              )}
              {v.status === "APPROVED" && !isLive && canRollback && (
                <button className="btn btn-ghost btn-sm"
                  style={{ fontSize: 12.5, gap: 6, color: "#d97706", borderColor: "#fde68a" }}
                  disabled={rollingBack === v.id}
                  onClick={() => handleRollback(v.id, v.versionNumber)}>
                  <Ic n="rotate_ccw" size={13} />
                  {rollingBack === v.id ? "Đang rollback..." : `Rollback về v${v.versionNumber}`}
                </button>
              )}
              {isDraft && canRollback && (
                <button className="btn btn-ghost btn-sm"
                  style={{ fontSize: 12.5, gap: 6, color: "#1d4ed8", borderColor: "#93c5fd" }}
                  disabled={rollingBack === v.id}
                  onClick={() => handleRollback(v.id, v.label || "bản nháp")}>
                  <Ic n="rotate_ccw" size={13} />
                  {rollingBack === v.id ? "Đang khôi phục..." : "Khôi phục bản nháp này"}
                </button>
              )}
              {(isDraft || v.status === "REJECTED") && (
                <button className="btn btn-ghost btn-sm"
                  style={{ fontSize: 12.5, gap: 6, color: "#dc2626", borderColor: "#fca5a5" }}
                  disabled={deletingDraft === v.id}
                  onClick={() => handleDeleteDraft(v.id)}>
                  <Ic n="x" size={13} />
                  {deletingDraft === v.id ? "Đang xóa..." : (isDraft ? "Xóa bản nháp" : "Xóa phiên bản từ chối")}
                </button>
              )}
            </div>
          </div>
        </div>
      );
    }

    return (
      <Section>
        <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 4 }}>
          <h2 className="t-h2">Lịch sử phiên bản</h2>
          {(course?.status === "PUBLISHED" || course?.status === "DRAFT") && hasPendingChanges && (
            <button className="btn btn-ghost btn-sm"
              style={{ gap: 6, borderColor: "#93c5fd", color: "#1d4ed8" }}
              disabled={savingDraft}
              onClick={() => setShowSaveDraft(true)}>
              <Ic n="download" size={14} />Lưu bản nháp
              {draftCount > 0 && <span style={{ background: "#e0f2fe", color: "#0284c7", borderRadius: 999, fontSize: 10, padding: "0 5px", fontWeight: 700 }}>{draftCount}/3</span>}
            </button>
          )}
        </div>
        <p className="muted" style={{ fontSize: 12.5, marginBottom: 20 }}>Lưu bản nháp để checkpoint tiến độ. Nộp duyệt khi sẵn sàng.</p>

        {historyLoading && <div className="muted" style={{ fontSize: 13.5 }}>Đang tải...</div>}

        {!historyLoading && draftVersions.length > 0 && (
          <div style={{ marginBottom: 20 }}>
            <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 10 }}>
              <span style={{ fontSize: 11, fontWeight: 700, color: "#64748b", textTransform: "uppercase", letterSpacing: "0.06em" }}>
                Bản nháp đã lưu ({draftCount}/3)
              </span>
              <div style={{ flex: 1, height: 1, background: "var(--border)" }} />
            </div>
            <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
              {draftVersions.map(v => <VersionCard key={v.id} v={v} />)}
            </div>
          </div>
        )}

        {!historyLoading && officialVersions.length === 0 && draftVersions.length === 0 && (
          <Empty icon="clock" title="Chưa có phiên bản nào" sub="Lịch sử sẽ xuất hiện sau khi bạn nộp hoặc lưu bản nháp." />
        )}

        {!historyLoading && officialVersions.length > 0 && (
          <div>
            {draftVersions.length > 0 && (
              <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 10 }}>
                <span style={{ fontSize: 11, fontWeight: 700, color: "#64748b", textTransform: "uppercase", letterSpacing: "0.06em" }}>Lịch sử nộp duyệt</span>
                <div style={{ flex: 1, height: 1, background: "var(--border)" }} />
              </div>
            )}
            <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
              {officialVersions.map(v => <VersionCard key={v.id} v={v} />)}
            </div>
          </div>
        )}
      </Section>
    );
  }

  /* ── Tab Lịch sử duyệt ── */
  const ACTION_CFG = {
    SUBMITTED_FIRST:  { label: "Nộp duyệt lần đầu",   color: "#0284c7", icon: "send"       },
    SUBMITTED_UPDATE: { label: "Nộp cập nhật",          color: "#0284c7", icon: "send"       },
    APPROVED:         { label: "Được duyệt",            color: "#16a34a", icon: "check"      },
    REJECTED:         { label: "Bị từ chối",            color: "#dc2626", icon: "x"          },
    WITHDRAWN:        { label: "Rút khỏi hàng duyệt",  color: "#d97706", icon: "rotate_ccw" },
  };

  function CourseHistoryTab({ history, historyLoading }: any) {
    return (
      <Section>
        <h2 className="t-h2" style={{ marginBottom: 16 }}>Lịch sử phê duyệt</h2>
        {historyLoading && <div className="muted" style={{ fontSize: 13.5 }}>Đang tải...</div>}
        {!historyLoading && history.length === 0 && (
          <div className="muted" style={{ fontSize: 13.5 }}>Chưa có hoạt động duyệt nào.</div>
        )}
        {!historyLoading && history.length > 0 && (
          <div style={{ display: "flex", flexDirection: "column", gap: 0, position: "relative" }}>
            <div style={{ position: "absolute", left: 15, top: 8, bottom: 8, width: 2, background: "var(--border)", borderRadius: 2 }} />
            {history.map((h: any, i: number) => {
              const cfg = ACTION_CFG[h.action] || { label: h.action, color: "#64748b", icon: "clock" };
              return (
                <div key={h.id || i} style={{ display: "flex", gap: 14, padding: "10px 0", alignItems: "flex-start" }}>
                  <div style={{ width: 32, height: 32, borderRadius: "50%", background: cfg.color + "18",
                    border: `2px solid ${cfg.color}`, display: "grid", placeItems: "center", flex: "none", zIndex: 1 }}>
                    <Ic n={cfg.icon} size={14} style={{ color: cfg.color }} />
                  </div>
                  <div style={{ flex: 1, paddingTop: 4 }}>
                    <div style={{ display: "flex", alignItems: "center", gap: 8, flexWrap: "wrap" }}>
                      <span style={{ fontWeight: 700, fontSize: 13.5, color: cfg.color }}>{cfg.label}</span>
                      <span className="muted" style={{ fontSize: 12 }}>{fmtDT(h.createdAt)}</span>
                    </div>
                    {h.reason && (
                      <div style={{ fontSize: 12.5, marginTop: 3, padding: "6px 10px", borderRadius: 7,
                        color: h.action === "REJECTED" ? "#dc2626" : "var(--text-2)",
                        background: h.action === "REJECTED" ? "#fff5f5" : "var(--surface-2)",
                        border: `1px solid ${h.action === "REJECTED" ? "#fecaca" : "var(--border)"}` }}>
                        {h.action === "REJECTED" ? "Lý do từ chối: " : ""}{h.reason}
                      </div>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </Section>
    );
  }

  Object.assign(window, { CourseVersionsTab, CourseHistoryTab });
})();
