// @ts-nocheck
/* ============================================================
     RIKKEI EDU – Rich text editor dùng chung (mô tả khóa học)
     contentEditable + execCommand — không phụ thuộc thư viện ngoài.
   ============================================================ */
(function () {
  const { useRef, useEffect } = React;
  const Ic = window.Icon;

  const FONT_SIZES = [
    { v: "2", label: "Nhỏ" },
    { v: "3", label: "Vừa" },
    { v: "5", label: "Lớn" },
  ];

  /**
   * Props:
   *   value    — HTML string hiện tại
   *   onChange — (html: string) => void
   *   placeholder
   *   minHeight — px, mặc định 160
   *   compact   — true: chỉ hiện nút Bold + Bullet (dùng cho form nhanh như Create Course Modal)
   *   disabled
   */
  function RichTextEditor({ value, onChange, placeholder, minHeight, compact, disabled }) {
    const ref = useRef(null);
    const isFocused = useRef(false);

    // Chỉ đồng bộ innerHTML từ prop khi editor KHÔNG đang được gõ (tránh nhảy con trỏ) —
    // ví dụ khi component mount lần đầu hoặc khi form bị reset từ bên ngoài (nút "Hủy").
    useEffect(() => {
      if (!ref.current) return;
      if (isFocused.current) return;
      if (ref.current.innerHTML !== (value || "")) {
        ref.current.innerHTML = value || "";
      }
    }, [value]);

    function emitChange() {
      onChange?.(ref.current?.innerHTML || "");
    }

    function exec(cmd, arg) {
      if (disabled) return;
      ref.current?.focus();
      document.execCommand(cmd, false, arg);
      emitChange();
    }

    function ToolBtn({ icon, label, onClick, style: extra }) {
      return (
        <button type="button" onClick={onClick} disabled={disabled}
          title={label}
          style={{
            width: 28, height: 26, borderRadius: 6, border: "1px solid var(--border)",
            background: "var(--surface)", color: "var(--text-2)", cursor: disabled ? "not-allowed" : "pointer",
            display: "flex", alignItems: "center", justifyContent: "center", fontSize: 12.5, fontWeight: 800,
            ...extra,
          }}>
          {icon || label}
        </button>
      );
    }

    return (
      <div style={{ border: "1px solid var(--border-input)", borderRadius: 8, overflow: "hidden", opacity: disabled ? 0.6 : 1 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 4, padding: 7, background: "var(--surface-2)", borderBottom: "1px solid var(--border)" }}>
          <ToolBtn label="B" style={{ fontWeight: 800 }} onClick={() => exec("bold")} />
          {!compact && <ToolBtn label="I" style={{ fontStyle: "italic" }} onClick={() => exec("italic")} />}
          {!compact && <ToolBtn label="U" style={{ textDecoration: "underline" }} onClick={() => exec("underline")} />}
          <ToolBtn label="≡" onClick={() => exec("insertUnorderedList")} />
          {!compact && (
            <>
              <span style={{ width: 1, height: 18, background: "var(--border)", margin: "0 4px" }} />
              <select disabled={disabled} onChange={e => exec("fontSize", e.target.value)} defaultValue="3"
                style={{ height: 26, borderRadius: 6, border: "1px solid var(--border)", background: "var(--surface)", fontSize: 12, color: "var(--text-2)", padding: "0 4px" }}>
                {FONT_SIZES.map(f => <option key={f.v} value={f.v}>{f.label}</option>)}
              </select>
            </>
          )}
        </div>
        <div
          ref={ref}
          contentEditable={!disabled}
          suppressContentEditableWarning
          onInput={emitChange}
          onFocus={() => { isFocused.current = true; }}
          onBlur={() => { isFocused.current = false; emitChange(); }}
          data-placeholder={placeholder || ""}
          className="rte-editable"
          style={{
            width: "100%", boxSizing: "border-box", minHeight: minHeight || 160,
            padding: "12px 14px", fontSize: 14, lineHeight: 1.6, color: "var(--text)", outline: "none",
          }}
        />
        <style>{`
          .rte-editable:empty:before { content: attr(data-placeholder); color: var(--text-3); }
          .rte-editable ul { margin: 0; padding-left: 20px; }
        `}</style>
      </div>
    );
  }

  Object.assign(window, { RichTextEditor });
})();
