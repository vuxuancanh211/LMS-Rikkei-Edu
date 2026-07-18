// @ts-nocheck
/* ============================================================
   RIKKEI EDU — Shared components
   ============================================================ */
import { createPortal } from 'react-dom';
import { connectAccountLockedSSE } from '../../services/notification-service';
import { useAuthStore } from '../../store';

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
   graded:{c:"success",t:"Đã chấm"}, submitted:{c:"info",t:"Đã nộp"}, returned:{c:"neutral",t:"Trả lại"}, not_submitted:{c:"neutral",t:"Chưa nộp",dot:"#94a3b8"},
  assignment_pending:{c:"warning",t:"Chờ chấm"},
  quiz_pending:{c:"info",t:"Chờ làm"},
  late:{c:"error",t:"Quá hạn"},
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

/* ---------- Course outcomes preview ("học được gì", dùng chung cho card giảng viên/học viên) ---------- */
function CourseOutcomesPreview({ outcomes, max, style }) {
  const list = (outcomes || []).filter(o => o.trim());
  const limit = max || 2;
  if (list.length === 0) return null;
  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 4, ...style }}>
      {list.slice(0, limit).map((o, i) => (
        <div key={i} className="row gap-6 t-xs" style={{ color: "var(--text-2)" }}>
          <span style={{ color: "#10b981" }}>✓</span><span className="truncate">{o}</span>
        </div>
      ))}
      {list.length > limit && <span className="t-xs muted">+{list.length - limit} mục khác</span>}
    </div>
  );
}

/* ---------- Course card ---------- */
function CourseCard({ c, onOpen, onCert, cta, preview }) {
  const ctaLabel = cta || (c.sStatus === "done" ? "Học lại" : c.sStatus === "new" ? "Bắt đầu học" : "Tiếp tục học");
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
        {preview && (
          <>
            {c.description && <p className="t-sm muted clamp-2" style={{ marginTop: 6 }}>{c.description}</p>}
            <CourseOutcomesPreview outcomes={c.learningOutcomes} style={{ marginTop: 6 }} />
            <div className="meta-row" style={{ marginTop: 6 }}><I n="users" size={15} /> {c.studentCount ?? 0} học viên</div>
          </>
        )}
        <div style={{ marginTop: "auto", paddingTop: 6 }}>
          {c.sStatus !== "new" && (
            <div style={{ marginBottom: 14 }}>
              <div className="between" style={{ marginBottom: 7 }}><span className="t-sm muted">Tiến độ</span></div>
              <Progress value={c.progress} />
            </div>
          )}
          {c.sStatus === "done" ? (
            <div style={{ display: "flex", gap: 8 }}>
              <button className="btn btn-primary" style={{ flex: 1, padding: "0 10px", fontSize: 13 }} onClick={(e) => { e.stopPropagation(); onOpen?.(); }}>
                <I n="rotate_ccw" size={15} />Học lại
              </button>
              <button className="btn btn-secondary" style={{ flex: 1, padding: "0 10px", fontSize: 13 }} onClick={(e) => { e.stopPropagation(); if (onCert) onCert(); else if (window.nav) window.nav("certs", { courseId: c.id }); else if (onOpen) onOpen(); }}>
                <I n="award" size={15} />Chứng chỉ
              </button>
            </div>
          ) : (
            <button className="btn btn-block btn-primary" onClick={(e) => { e.stopPropagation(); onOpen?.(); }}>
              {ctaLabel}
            </button>
          )}
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
function Section({ title, sub, action, children, pad, style, className, bodyStyle }) {
  return (
    <div className={`card section-card fade-in ${className || ""}`} style={style}>
      {(title || action) && (
        <div className="section-head">
          <div><h3 className="t-h3">{title}</h3>{sub && <div className="t-sm muted" style={{ marginTop: 3 }}>{sub}</div>}</div>
          {action}
        </div>
      )}
      <div style={{ padding: pad === false ? 0 : 22, ...bodyStyle }}>{children}</div>
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
  const windowSize = 5;
  let start = Math.max(1, page - Math.floor(windowSize / 2));
  let end = Math.min(pages, start + windowSize - 1);
  start = Math.max(1, end - windowSize + 1);
  const nums = [];
  for (let i = start; i <= end; i++) nums.push(i);
  return (
    <div className="pager">
      <button onClick={() => onPage(Math.max(1, page - 1))} disabled={page === 1}><I n="chevron_left" size={16} /></button>
      {start > 1 && <>
        <button onClick={() => onPage(1)}>1</button>
        {start > 2 && <span style={{ color: "var(--text-3)", padding: "0 4px" }}>…</span>}
      </>}
      {nums.map((n) => <button key={n} className={n === page ? "on" : ""} onClick={() => onPage(n)}>{n}</button>)}
      {end < pages && <>
        {end < pages - 1 && <span style={{ color: "var(--text-3)", padding: "0 4px" }}>…</span>}
        <button onClick={() => onPage(pages)}>{pages}</button>
      </>}
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
        <div><h3 className="t-h2" style={{ margin: 0 }}>{title}</h3>{sub && <div className="t-sm muted" style={{ marginTop: 3 }}>{sub}</div>}</div>
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
function calculateYAxis(data = [], unit = "") {
  const rawMax = Math.max(...data, 0);
  const u = (unit || "").replace(/^\(|\)$/g, "").trim();
  const isPercent = u === "%" || u.toLowerCase() === "phần trăm";

  if (isPercent && rawMax <= 100) {
    const gridY = [0, 1, 2, 3, 4, 5].map((t) => ({
      posRatio: t / 5,
      val: t * 20
    }));
    return { max: 100, min: 0, step: 20, gridY, u };
  }

  if (rawMax === 0) {
    const max = isPercent ? 100 : 10;
    const step = isPercent ? 20 : 2;
    const gridY = [0, 1, 2, 3, 4, 5].map((t) => ({
      posRatio: t / 5,
      val: t * step
    }));
    return { max, min: 0, step, gridY, u };
  }

  const targetStep = rawMax / 5;
  const mag = Math.pow(10, Math.floor(Math.log10(targetStep)));
  const norm = targetStep / mag;

  let niceNorm;
  if (norm <= 1) niceNorm = 1;
  else if (norm <= 2) niceNorm = 2;
  else if (norm <= 5) niceNorm = 5;
  else niceNorm = 10;

  const step = Math.max(1, Math.ceil(niceNorm * mag));
  const max = step * 5;
  const gridY = [0, 1, 2, 3, 4, 5].map((t) => ({
    posRatio: t / 5,
    val: t * step
  }));

  return { max, min: 0, step, gridY, u };
}

function LineChart({ data = [], labels = [], color = "#2563eb", height = 240, unit = "", yUnit = "" }) {
  const [hoverIdx, setHoverIdx] = useState(null);
  const containerRef = useRef(null);
  const { max, min, gridY, u } = calculateYAxis(data, unit || yUnit);
  const w = 640, h = height, pad = u ? 64 : 44, padB = 28;
  const len = data.length || 1;
  const x = (i) => (len <= 1 ? pad + (w - pad - 12) / 2 : pad + (i * (w - pad - 12)) / (len - 1));
  const y = (v) => h - padB - ((v - min) / (max - min)) * (h - pad - padB);
  const pts = data.map((v, i) => [x(i), y(v)]);
  const line = pts.map((p, i) => (i ? "L" : "M") + p[0] + " " + p[1]).join(" ");
  const area = pts.length > 0 ? line + ` L ${x(data.length - 1)} ${h - padB} L ${x(0)} ${h - padB} Z` : "";

  const handleMouseMove = (e) => {
    if (!containerRef.current || len === 0) return;
    const rect = containerRef.current.getBoundingClientRect();
    const relX = e.clientX - rect.left;
    const svgX = relX * (w / rect.width);
    if (svgX < pad - 15 || svgX > w + 10) {
      if (hoverIdx !== null) setHoverIdx(null);
      return;
    }
    let closestI = 0;
    let minDist = Infinity;
    for (let i = 0; i < len; i++) {
      const dist = Math.abs(x(i) - svgX);
      if (dist < minDist) {
        minDist = dist;
        closestI = i;
      }
    }
    if (closestI !== hoverIdx) {
      setHoverIdx(closestI);
    }
  };

  const handleMouseLeave = () => {
    setHoverIdx(null);
  };

  return (
    <div
      ref={containerRef}
      onMouseMove={handleMouseMove}
      onMouseLeave={handleMouseLeave}
      style={{ position: "relative", width: "100%", height, userSelect: "none" }}
    >
      <svg viewBox={`0 0 ${w} ${h}`} style={{ width: "100%", height, display: "block" }} preserveAspectRatio="none">
        <defs>
          <linearGradient id="lg" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor={color} stopOpacity="0.18" />
            <stop offset="100%" stopColor={color} stopOpacity="0" />
          </linearGradient>
        </defs>
        {u && (
          <text x={pad - 4} y={16} textAnchor="end" fontSize="11.5" fill="#64748b" fontWeight="600">
            ({u})
          </text>
        )}
        {gridY.map((gy, i) => {
          const pos = h - padB - gy.posRatio * (h - pad - padB);
          return (
            <g key={i}>
              <line x1={pad} y1={pos} x2={w - 12} y2={pos} stroke="#eef2f7" strokeWidth="1" />
              <text x={pad - 6} y={pos + 4} textAnchor="end" fontSize="11" fill="#94a3b8" fontWeight="500">
                {typeof gy.val === "number" ? gy.val.toLocaleString("vi-VN") : gy.val}
              </text>
            </g>
          );
        })}
        {area && <path d={area} fill="url(#lg)" />}
        {line && (
          <path d={line} fill="none" stroke={color} strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" />
        )}
        {hoverIdx !== null && (
          <line
            x1={x(hoverIdx)}
            y1={pad}
            x2={x(hoverIdx)}
            y2={h - padB}
            stroke={color}
            strokeWidth="1.5"
            strokeDasharray="4,4"
            opacity="0.6"
          />
        )}
        {pts.map((p, i) => {
          const isHovered = hoverIdx === i;
          return (
            <g key={i} style={{ cursor: "pointer" }} onMouseEnter={() => setHoverIdx(i)}>
              {isHovered && <circle cx={p[0]} cy={p[1]} r="9" fill={color} opacity="0.25" />}
              <circle
                cx={p[0]}
                cy={p[1]}
                r={isHovered ? "5.5" : "3.5"}
                fill="#fff"
                stroke={color}
                strokeWidth={isHovered ? "3" : "2.5"}
                style={{ transition: "r 0.15s ease, stroke-width 0.15s ease" }}
              />
            </g>
          );
        })}
        {labels.map((l, i) => {
          const isHovered = hoverIdx === i;
          return (
            <text
              key={i}
              x={x(i)}
              y={h - 8}
              textAnchor="middle"
              fontSize={isHovered ? "12" : "11.5"}
              fill={isHovered ? color : "#94a3b8"}
              fontWeight={isHovered ? "700" : "500"}
              style={{ transition: "all 0.15s ease" }}
            >
              {l}
            </text>
          );
        })}
      </svg>

      {hoverIdx !== null && data[hoverIdx] !== undefined && (
        <div
          style={{
            position: "absolute",
            left: `${(x(hoverIdx) / w) * 100}%`,
            top: `${(y(data[hoverIdx]) / h) * 100}%`,
            transform: (() => {
              const leftP = (x(hoverIdx) / w) * 100;
              const topP = (y(data[hoverIdx]) / h) * 100;
              const hShift = leftP < 20 ? "0%" : leftP > 80 ? "-100%" : "-50%";
              const vShift = topP < 30 ? "12px" : "calc(-100% - 12px)";
              return `translate(${hShift}, ${vShift})`;
            })(),
            pointerEvents: "none",
            zIndex: 30,
            background: "rgba(15, 23, 42, 0.95)",
            backdropFilter: "blur(8px)",
            border: "1px solid rgba(255, 255, 255, 0.18)",
            color: "#fff",
            padding: "8px 12px",
            borderRadius: "8px",
            boxShadow: "0 10px 25px -5px rgba(0, 0, 0, 0.45), 0 4px 10px -2px rgba(0, 0, 0, 0.2)",
            fontSize: "12.5px",
            fontWeight: 600,
            whiteSpace: "nowrap",
            transition: "left 0.1s ease-out, top 0.1s ease-out, transform 0.1s ease-out"
          }}
        >
          <div style={{ fontSize: "11px", color: "#94a3b8", marginBottom: "3px", fontWeight: 500 }}>
            {labels[hoverIdx]}
          </div>
          <div style={{ fontSize: "14px", color: "#38bdf8", fontWeight: 700, display: "flex", alignItems: "center", gap: "6px" }}>
            <span style={{ width: "7px", height: "7px", borderRadius: "50%", background: color, display: "inline-block" }} />
            <span>
              {typeof data[hoverIdx] === "number" ? data[hoverIdx].toLocaleString("vi-VN") : data[hoverIdx]}
              {u ? ` ${u}` : ""}
            </span>
          </div>
        </div>
      )}
    </div>
  );
}

function BarChart({ data = [], labels = [], color = "#0f172a", height = 240, unit = "", yUnit = "" }) {
  const [hoverIdx, setHoverIdx] = useState(null);
  const containerRef = useRef(null);
  const { max, min, gridY, u } = calculateYAxis(data, unit || yUnit);
  const w = 640, h = height, pad = u ? 64 : 44, padB = 28;
  const len = data.length || 1;
  const bw = (w - pad - 12) / len;
  const xCenter = (i) => pad + i * bw + bw * 0.5;
  const yTop = (v) => h - padB - ((v - min) / (max - min)) * (h - pad - padB);

  const handleMouseMove = (e) => {
    if (!containerRef.current || len === 0) return;
    const rect = containerRef.current.getBoundingClientRect();
    const relX = e.clientX - rect.left;
    const svgX = relX * (w / rect.width);
    if (svgX < pad - 15 || svgX > w + 10) {
      if (hoverIdx !== null) setHoverIdx(null);
      return;
    }
    let closestI = 0;
    let minDist = Infinity;
    for (let i = 0; i < len; i++) {
      const dist = Math.abs(xCenter(i) - svgX);
      if (dist < minDist) {
        minDist = dist;
        closestI = i;
      }
    }
    if (closestI !== hoverIdx) {
      setHoverIdx(closestI);
    }
  };

  const handleMouseLeave = () => {
    setHoverIdx(null);
  };

  return (
    <div
      ref={containerRef}
      onMouseMove={handleMouseMove}
      onMouseLeave={handleMouseLeave}
      style={{ position: "relative", width: "100%", height, userSelect: "none" }}
    >
      <svg viewBox={`0 0 ${w} ${h}`} style={{ width: "100%", height, display: "block" }} preserveAspectRatio="none">
        {u && (
          <text x={pad - 4} y={16} textAnchor="end" fontSize="11.5" fill="#64748b" fontWeight="600">
            ({u})
          </text>
        )}
        {gridY.map((gy, i) => {
          const pos = h - padB - gy.posRatio * (h - pad - padB);
          return (
            <g key={i}>
              <line x1={pad} y1={pos} x2={w - 12} y2={pos} stroke="#eef2f7" strokeWidth="1" />
              <text x={pad - 6} y={pos + 4} textAnchor="end" fontSize="11" fill="#94a3b8" fontWeight="500">
                {typeof gy.val === "number" ? gy.val.toLocaleString("vi-VN") : gy.val}
              </text>
            </g>
          );
        })}
        {data.map((v, i) => {
          const bh = ((v - min) / (max - min)) * (h - pad - padB);
          const bx = pad + i * bw + bw * 0.22, by = h - padB - Math.max(bh, 0);
          const isHovered = hoverIdx === i;
          return (
            <g key={i} style={{ cursor: "pointer" }} onMouseEnter={() => setHoverIdx(i)}>
              <rect
                x={bx}
                y={by}
                width={bw * 0.56}
                height={Math.max(bh, 0)}
                rx="6"
                fill={color}
                opacity={hoverIdx === null ? 0.88 : isHovered ? 1 : 0.4}
                style={{ transition: "opacity 0.15s ease, filter 0.15s ease" }}
              />
              <text
                x={bx + bw * 0.28}
                y={h - 8}
                textAnchor="middle"
                fontSize={isHovered ? "12" : "11.5"}
                fill={isHovered ? color : "#94a3b8"}
                fontWeight={isHovered ? "700" : "500"}
                style={{ transition: "all 0.15s ease" }}
              >
                {labels[i]}
              </text>
            </g>
          );
        })}
      </svg>

      {hoverIdx !== null && data[hoverIdx] !== undefined && (
        <div
          style={{
            position: "absolute",
            left: `${(xCenter(hoverIdx) / w) * 100}%`,
            top: `${(yTop(data[hoverIdx]) / h) * 100}%`,
            transform: (() => {
              const leftP = (xCenter(hoverIdx) / w) * 100;
              const topP = (yTop(data[hoverIdx]) / h) * 100;
              const hShift = leftP < 20 ? "0%" : leftP > 80 ? "-100%" : "-50%";
              const vShift = topP < 30 ? "12px" : "calc(-100% - 10px)";
              return `translate(${hShift}, ${vShift})`;
            })(),
            pointerEvents: "none",
            zIndex: 30,
            background: "rgba(15, 23, 42, 0.95)",
            backdropFilter: "blur(8px)",
            border: "1px solid rgba(255, 255, 255, 0.18)",
            color: "#fff",
            padding: "8px 12px",
            borderRadius: "8px",
            boxShadow: "0 10px 25px -5px rgba(0, 0, 0, 0.45), 0 4px 10px -2px rgba(0, 0, 0, 0.2)",
            fontSize: "12.5px",
            fontWeight: 600,
            whiteSpace: "nowrap",
            transition: "left 0.1s ease-out, top 0.1s ease-out, transform 0.1s ease-out"
          }}
        >
          <div style={{ fontSize: "11px", color: "#94a3b8", marginBottom: "3px", fontWeight: 500 }}>
            {labels[hoverIdx]}
          </div>
          <div style={{ fontSize: "14px", color: "#38bdf8", fontWeight: 700, display: "flex", alignItems: "center", gap: "6px" }}>
            <span style={{ width: "7px", height: "7px", borderRadius: "50%", background: color, display: "inline-block" }} />
            <span>
              {typeof data[hoverIdx] === "number" ? data[hoverIdx].toLocaleString("vi-VN") : data[hoverIdx]}
              {u ? ` ${u}` : ""}
            </span>
          </div>
        </div>
      )}
    </div>
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

/* ---------- AccountLockedOverlay ---------- */
function AccountLockedOverlay() {
  const [lockedData, setLockedData] = useState(null);

  useEffect(() => {
    window.__triggerAccountLockedModal = (data) => {
      setLockedData(data || { message: "Tài khoản của bạn đã bị quản trị viên khóa hoặc vô hiệu hóa quyền truy cập." });
    };
    const disconnectLocked = connectAccountLockedSSE((data) => {
      if (window.__triggerAccountLockedModal) {
        window.__triggerAccountLockedModal(data);
      } else {
        setLockedData(data || { message: "Tài khoản của bạn đã bị quản trị viên khóa hoặc vô hiệu hóa quyền truy cập." });
      }
    });

    /* Multi-tab Sync: Khi 1 tab bấm Xác nhận hoặc đăng xuất, các tab song song khác tự động nhảy về /login */
    const handleStorage = (e) => {
      if (e.key === 'account_locked_sync' || (e.key === 'auth-storage' && (!e.newValue || e.newValue.includes('"isAuthenticated":false')))) {
        if (window.location.pathname !== '/login') {
          useAuthStore.getState().logout();
          sessionStorage.clear();
          window.location.href = '/login';
        }
      }
    };
    window.addEventListener('storage', handleStorage);

    return () => {
      disconnectLocked();
      if (window.__triggerAccountLockedModal) delete window.__triggerAccountLockedModal;
      window.removeEventListener('storage', handleStorage);
    };
  }, []);

  if (!lockedData) return null;

  const Ico = window.Icon;
  return (
    <div style={{
      position: "fixed", top: 0, left: 0, right: 0, bottom: 0,
      background: "rgba(15, 23, 42, 0.88)", backdropFilter: "blur(8px)",
      zIndex: 9999999, display: "flex", alignItems: "center", justifyContent: "center",
      padding: 20
    }} onClick={(e) => e.stopPropagation()}>
      <div style={{
        background: "var(--bg-card, #fff)", borderRadius: 16, maxWidth: 440, width: "100%",
        padding: "28px 24px", boxShadow: "0 25px 50px -12px rgba(0, 0, 0, 0.5)",
        textAlign: "center", border: "1px solid var(--border)", animation: "popIn 0.2s ease-out"
      }} onClick={(e) => e.stopPropagation()}>
        <div style={{
          width: 64, height: 64, borderRadius: "50%", background: "rgba(239, 68, 68, 0.12)",
          color: "#ef4444", display: "flex", alignItems: "center", justifyContent: "center",
          margin: "0 auto 18px"
        }}>
          {Ico && <Ico n="lock" size={32} />}
        </div>
        <h3 style={{ fontSize: 20, fontWeight: 700, margin: "0 0 10px", color: "var(--text-1, #0f172a)" }}>
          {lockedData.title || "Tài khoản đã bị khóa"}
        </h3>
        <p style={{ fontSize: 14.5, lineHeight: 1.6, color: "var(--text-2, #475569)", margin: "0 0 26px" }}>
          {lockedData.message || "Tài khoản của bạn đã bị quản trị viên khóa hoặc vô hiệu hóa quyền truy cập. Bạn vui lòng bấm nút bên dưới để xác nhận và quay về trang đăng nhập."}
        </p>
        <button
          className="btn btn-primary"
          style={{
            width: "100%", padding: "12px 20px", fontSize: 15, fontWeight: 600,
            background: "#ef4444", borderColor: "#ef4444", borderRadius: 10,
            color: "#fff", cursor: "pointer"
          }}
          onClick={() => {
            try { localStorage.setItem('account_locked_sync', Date.now().toString()); } catch (err) { void err; }
            useAuthStore.getState().logout();
            sessionStorage.clear();
            window.location.href = "/login";
          }}
        >
          Xác nhận
        </button>
      </div>
    </div>
  );
}

Object.assign(window, { Avatar, Status, STATUS, Progress, StatCard, CourseCard, CourseOutcomesPreview, Search, Tabs, Select, Section, Pager, PageBar, usePaged, Modal, ModalHead, ConfirmModal, AlertModal, AccountLockedOverlay, Empty, LineChart, BarChart, Donut });
