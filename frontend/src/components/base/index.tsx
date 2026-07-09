// @ts-nocheck
/* ============================================================
   RIKKEI EDU — Shared components
   ============================================================ */
import { createPortal } from 'react-dom';

const { useState, useEffect, useRef } = React;
const I = window.Icon;
const { avatarColor, initials } = window.UI;

/* ---------- Avatar ---------- */
function Avatar({ name, size = 38, src, ring }) {
  const st = { width: size, height: size, fontSize: size * 0.38,
    boxShadow: ring ? "0 0 0 2px #fff, 0 0 0 4px " + ring : "none" };
  if (src) return <span className="avatar" style={{ ...st, backgroundImage: `url(${src})` }} />;
  return <span className="avatar" style={{ ...st, background: avatarColor(name) }}>{initials(name)}</span>;
}

/* ---------- Status chips ---------- */
const STATUS = {
  learning:{c:"info",t:"Đang học",dot:"#2563eb"}, done:{c:"success",t:"Hoàn thành"}, new:{c:"neutral",t:"Chưa bắt đầu",dot:"#94a3b8"},
  active:{c:"success",t:"Hoạt động"}, disabled:{c:"error",t:"Vô hiệu hóa"},
  pending:{c:"warning",t:"Chờ duyệt"}, approved:{c:"success",t:"Đã duyệt"}, rejected:{c:"error",t:"Từ chối"},
  published:{c:"success",t:"Đã xuất bản"}, draft:{c:"neutral",t:"Bản nháp"},
  graded:{c:"success",t:"Đã chấm"}, submitted:{c:"info",t:"Đã nộp"}, late:{c:"error",t:"Trễ hạn"},
};
function Status({ s }) {
  const m = STATUS[s] || { c: "neutral", t: s };
  return <span className={"chip chip-" + m.c}>{m.dot && <span className="dot" style={{ background: m.dot }} />}{m.t}</span>;
}

/* ---------- Progress bar ---------- */
function Progress({ value, done }) {
  return (
    <div className="row gap-12">
      <div className="bar grow" style={done ? null : null}>
        <span className={done ? "" : ""} style={{ width: value + "%", background: done || value >= 100 ? "var(--success)" : "var(--primary)" }} />
      </div>
      <span style={{ fontSize: 13, fontWeight: 700, color: value >= 100 ? "var(--success)" : "var(--text)", minWidth: 38, textAlign: "right" }}>{value}%</span>
    </div>
  );
}

/* ---------- Stat card ---------- */
function StatCard({ icon, iconBg, iconColor, value, label, sub, trend }) {
  return (
    <div className="card stat fade-in">
      <div className="stat-top">
        <div className="stat-ic" style={{ background: iconBg, color: iconColor }}><I n={icon} size={23} /></div>
        {trend != null && (
          <span className={"chip chip-" + (trend >= 0 ? "success" : "error")}>
            <I n="trending" size={13} />{trend >= 0 ? "+" : ""}{trend}%
          </span>
        )}
      </div>
      <div>
        <div className="stat-lbl">{label}</div>
        <div className="stat-val" style={{ marginTop: 6 }}>{value}</div>
      </div>
      {sub && <div className="stat-sub">{sub}</div>}
    </div>
  );
}

/* ---------- Course card ---------- */
function CourseCard({ c, onOpen, cta }) {
  const ctaLabel = cta || (c.sStatus === "done" ? "Xem chứng chỉ" : c.sStatus === "new" ? "Bắt đầu học" : "Tiếp tục học");
  return (
    <div className="card course-card fade-in" onClick={onOpen} style={{ cursor: "pointer" }}>
      <div className="course-thumb" style={{ backgroundImage: `url(${c.thumb})` }}>
        <span className="tl"><span className="chip" style={{ background: "rgba(15,23,42,.72)", color: "#fff", backdropFilter: "blur(4px)" }}>{c.cat}</span></span>
        <span className="tr"><Status s={c.sStatus} /></span>
      </div>
      <div className="course-body">
        <h3 className="clamp-2">{c.title}</h3>
        <div className="meta-row"><I n="user" size={15} /> {c.instructor}</div>
        <div className="row gap-16 wrap" style={{ marginTop: -2 }}>
          <span className="meta-row"><I n="book_open" size={15} /> {c.lessons} bài</span>
          <span className="meta-row"><I n="clock" size={15} /> {c.hours} giờ</span>
          {c.rating > 0 && <span className="meta-row" style={{ color: "var(--warning)" }}><I n="star" size={15} fill="currentColor" /> <b style={{ color: "var(--text)" }}>{c.rating}</b></span>}
        </div>
        <div style={{ marginTop: "auto", paddingTop: 6 }}>
          {c.sStatus !== "new" && (
            <div style={{ marginBottom: 14 }}>
              <div className="between" style={{ marginBottom: 7 }}><span className="t-sm muted">Tiến độ</span></div>
              <Progress value={c.progress} />
            </div>
          )}
          <button className={"btn btn-block " + (c.sStatus === "done" ? "btn-ghost" : "btn-primary")}>
            {c.sStatus === "done" && <I n="award" size={16} />}{ctaLabel}
          </button>
        </div>
      </div>
    </div>
  );
}

/* ---------- Search input ---------- */
function Search({ placeholder, value, onChange, style }) {
  return (
    <div className="field-icon grow" style={style}>
      <I n="search" />
      <input className="input" placeholder={placeholder} value={value || ""} onChange={(e) => onChange && onChange(e.target.value)} />
    </div>
  );
}

/* ---------- Tabs ---------- */
function Tabs({ items, value, onChange }) {
  return (
    <div className="tabs">
      {items.map((it) => (
        <button key={it.v} className={value === it.v ? "on" : ""} onClick={() => onChange(it.v)}>
          {it.label}{it.count != null && <span style={{ opacity: .7, marginLeft: 5 }}>({it.count})</span>}
        </button>
      ))}
    </div>
  );
}

/* ---------- Select ---------- */
function Select({ value, onChange, options, style, name }) {
  return (
    <div style={{ position: "relative", ...style }}>
      <select className="select" name={name} value={value} onChange={(e) => onChange(e.target.value)} style={{ appearance: "none", paddingRight: 38, cursor: "pointer" }}>
        {options.map((o) => <option key={o.v ?? o} value={o.v ?? o}>{o.label ?? o}</option>)}
      </select>
      <I n="chevron_down" size={18} style={{ position: "absolute", right: 13, top: "50%", transform: "translateY(-50%)", color: "var(--text-3)", pointerEvents: "none" }} />
    </div>
  );
}

/* ---------- Section card ---------- */
function Section({ title, sub, action, children, pad }) {
  return (
    <div className="card section-card fade-in">
      {(title || action) && (
        <div className="section-head">
          <div><h3 className="t-h3">{title}</h3>{sub && <div className="t-sm muted" style={{ marginTop: 3 }}>{sub}</div>}</div>
          {action}
        </div>
      )}
      <div style={{ padding: pad === false ? 0 : 22 }}>{children}</div>
    </div>
  );
}

/* ---------- Pagination ---------- */
function usePaged(list, size) {
  const [page, setPage] = React.useState(1);
  const pages = Math.max(1, Math.ceil(list.length / size));
  const cur = Math.min(page, pages);
  const slice = list.slice((cur - 1) * size, cur * size);
  return { slice, page: cur, pages, setPage, total: list.length, from: list.length ? (cur - 1) * size + 1 : 0, to: Math.min(cur * size, list.length) };
}
function PageBar({ pg, unit, forcePager }) {
  if (pg.total === 0) return null;
  return (
    <div className="between wrap pagebar" style={{ gap: 12 }}>
      <span className="t-sm muted">Hiển thị <b style={{ color: "var(--text)" }}>{pg.from}–{pg.to}</b> trong tổng số <b style={{ color: "var(--text)" }}>{pg.total}</b> {unit || "mục"}</span>
      {(forcePager || pg.pages > 1) && <Pager page={pg.page} pages={pg.pages} onPage={pg.setPage} />}
    </div>
  );
}

function Pager({ page, pages, onPage }) {
  const nums = [];
  for (let i = 1; i <= Math.min(pages, 5); i++) nums.push(i);
  return (
    <div className="pager">
      <button onClick={() => onPage(Math.max(1, page - 1))} disabled={page === 1}><I n="chevron_left" size={16} /></button>
      {nums.map((n) => <button key={n} className={n === page ? "on" : ""} onClick={() => onPage(n)}>{n}</button>)}
      {pages > 5 && <><span style={{ color: "var(--text-3)", padding: "0 4px" }}>…</span><button onClick={() => onPage(pages)}>{pages}</button></>}
      <button onClick={() => onPage(Math.min(pages, page + 1))} disabled={page === pages}><I n="chevron_right" size={16} /></button>
    </div>
  );
}

/* ---------- Modal ---------- */
function Modal({ open, onClose, children, max, maxHeight }) {
  useEffect(() => {
    if (!open) return;
    const h = (e) => e.key === "Escape" && onClose();
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    window.addEventListener("keydown", h);
    return () => {
      document.body.style.overflow = previousOverflow;
      window.removeEventListener("keydown", h);
    };
  }, [open]);
  if (!open) return null;
  return createPortal(
    <div className="overlay" onClick={onClose}>
      <div className="modal" style={{ maxWidth: max, maxHeight }} onClick={(e) => e.stopPropagation()}>{children}</div>
    </div>,
    document.body
  );
}
function ModalHead({ title, sub, onClose, icon, iconBg, iconColor }) {
  return (
    <div className="modal-head">
      <div className="row gap-12">
        {icon && <div className="stat-ic" style={{ width: 42, height: 42, background: iconBg, color: iconColor }}><I n={icon} size={21} /></div>}
        <div><h3 className="t-h2">{title}</h3>{sub && <div className="t-sm muted" style={{ marginTop: 3 }}>{sub}</div>}</div>
      </div>
      <button className="icon-btn" style={{ width: 36, height: 36 }} onClick={onClose}><I n="x" size={18} /></button>
    </div>
  );
}

/* ---------- Empty state ---------- */
function Empty({ icon, title, text, action }) {
  return (
    <div className="empty">
      <div className="ic"><I n={icon || "folder"} size={30} /></div>
      <div className="t-h3" style={{ color: "var(--text)" }}>{title}</div>
      {text && <div style={{ marginTop: 6, maxWidth: 380, margin: "6px auto 0" }}>{text}</div>}
      {action && <div style={{ marginTop: 18 }}>{action}</div>}
    </div>
  );
}

/* ============================================================
   CHARTS (inline SVG, data viz)
   ============================================================ */
function LineChart({ data, labels, color = "#2563eb", height = 240 }) {
  const w = 640, h = height, pad = 36, padB = 28;
  const max = Math.max(...data) * 1.1, min = 0;
  const x = (i) => pad + (i * (w - pad - 12)) / (data.length - 1);
  const y = (v) => h - padB - ((v - min) / (max - min)) * (h - pad - padB);
  const pts = data.map((v, i) => [x(i), y(v)]);
  const line = pts.map((p, i) => (i ? "L" : "M") + p[0] + " " + p[1]).join(" ");
  const area = line + ` L ${x(data.length - 1)} ${h - padB} L ${pad} ${h - padB} Z`;
  const gridY = [0, 0.25, 0.5, 0.75, 1].map((t) => h - padB - t * (h - pad - padB));
  return (
    <svg viewBox={`0 0 ${w} ${h}`} style={{ width: "100%", height }} preserveAspectRatio="none">
      <defs><linearGradient id="lg" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stopColor={color} stopOpacity="0.18" /><stop offset="100%" stopColor={color} stopOpacity="0" /></linearGradient></defs>
      {gridY.map((gy, i) => <line key={i} x1={pad} y1={gy} x2={w - 12} y2={gy} stroke="#eef2f7" strokeWidth="1" />)}
      <path d={area} fill="url(#lg)" />
      <path d={line} fill="none" stroke={color} strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" />
      {pts.map((p, i) => <circle key={i} cx={p[0]} cy={p[1]} r="3.5" fill="#fff" stroke={color} strokeWidth="2.5" />)}
      {labels.map((l, i) => <text key={i} x={x(i)} y={h - 8} textAnchor="middle" fontSize="11.5" fill="#94a3b8" fontWeight="500">{l}</text>)}
    </svg>
  );
}
function BarChart({ data, labels, color = "#0f172a", height = 240 }) {
  const w = 640, h = height, pad = 36, padB = 28;
  const max = Math.max(...data) * 1.15;
  const bw = (w - pad - 12) / data.length;
  const gridY = [0, 0.25, 0.5, 0.75, 1].map((t) => h - padB - t * (h - pad - padB));
  return (
    <svg viewBox={`0 0 ${w} ${h}`} style={{ width: "100%", height }} preserveAspectRatio="none">
      {gridY.map((gy, i) => <line key={i} x1={pad} y1={gy} x2={w - 12} y2={gy} stroke="#eef2f7" strokeWidth="1" />)}
      {data.map((v, i) => {
        const bh = (v / max) * (h - pad - padB);
        const bx = pad + i * bw + bw * 0.22, by = h - padB - bh;
        return <g key={i}><rect x={bx} y={by} width={bw * 0.56} height={bh} rx="6" fill={color} opacity={0.88} />
          <text x={bx + bw * 0.28} y={h - 8} textAnchor="middle" fontSize="11.5" fill="#94a3b8" fontWeight="500">{labels[i]}</text></g>;
      })}
    </svg>
  );
}
function Donut({ value, size = 132, color = "#10b981", track = "#e6ecf4", label }) {
  const r = size / 2 - 11, c = 2 * Math.PI * r, off = c - (value / 100) * c;
  return (
    <div style={{ position: "relative", width: size, height: size }}>
      <svg width={size} height={size} style={{ transform: "rotate(-90deg)" }}>
        <circle cx={size / 2} cy={size / 2} r={r} fill="none" stroke={track} strokeWidth="11" />
        <circle cx={size / 2} cy={size / 2} r={r} fill="none" stroke={color} strokeWidth="11" strokeLinecap="round" strokeDasharray={c} strokeDashoffset={off} style={{ transition: "stroke-dashoffset .6s" }} />
      </svg>
      <div style={{ position: "absolute", inset: 0, display: "grid", placeItems: "center", textAlign: "center" }}>
        <div><div style={{ fontSize: 26, fontWeight: 800, letterSpacing: "-.02em" }}>{value}%</div>{label && <div className="t-xs muted">{label}</div>}</div>
      </div>
    </div>
  );
}

/* ---------- ConfirmModal ---------- */
function ConfirmModal({ open, onClose, onConfirm, title, message, confirmLabel = "Xác nhận", cancelLabel = "Hủy", danger = false }) {
  if (!open) return null;
  return (
    <Modal open={open} onClose={onClose} max={420}>
      <ModalHead title={title || "Xác nhận"} icon={danger ? "warn" : "help"} iconBg={danger ? "#fee2e2" : "#fef9c3"} iconColor={danger ? "#dc2626" : "#d97706"} onClose={onClose} />
      <div className="modal-body">
        <p style={{ fontSize: 14.5, lineHeight: 1.6, color: "var(--text)", whiteSpace: "pre-wrap", margin: 0 }}>{message}</p>
      </div>
      <div className="modal-foot">
        <button className="btn btn-ghost" onClick={onClose}>{cancelLabel}</button>
        <button className={`btn ${danger ? "btn-danger" : "btn-primary"}`} onClick={() => { onConfirm?.(); onClose(); }}>
          {confirmLabel}
        </button>
      </div>
    </Modal>
  );
}

/* ---------- AlertModal ---------- */
function AlertModal({ open, onClose, title, message, type = "error" }) {
  if (!open) return null;
  const cfg = {
    error:   { icon: "warn",  iconBg: "#fee2e2", iconColor: "#dc2626" },
    warning: { icon: "warn",  iconBg: "#fef9c3", iconColor: "#d97706" },
    success: { icon: "check_circle",   iconBg: "#dcfce7", iconColor: "#16a34a" },
    info:    { icon: "info",           iconBg: "#eaf1ff", iconColor: "#2563eb" },
  }[type] || { icon: "info", iconBg: "#eaf1ff", iconColor: "#2563eb" };
  return (
    <Modal open={open} onClose={onClose} max={400}>
      <ModalHead title={title || "Thông báo"} icon={cfg.icon} iconBg={cfg.iconBg} iconColor={cfg.iconColor} onClose={onClose} />
      <div className="modal-body">
        <p style={{ fontSize: 14.5, lineHeight: 1.6, color: "var(--text)", whiteSpace: "pre-wrap", margin: 0 }}>{message}</p>
      </div>
      <div className="modal-foot">
        <button className="btn btn-primary" onClick={onClose}>Đã hiểu</button>
      </div>
    </Modal>
  );
}

Object.assign(window, { Avatar, Status, STATUS, Progress, StatCard, CourseCard, Search, Tabs, Select, Section, Pager, PageBar, usePaged, Modal, ModalHead, ConfirmModal, AlertModal, Empty, LineChart, BarChart, Donut });
