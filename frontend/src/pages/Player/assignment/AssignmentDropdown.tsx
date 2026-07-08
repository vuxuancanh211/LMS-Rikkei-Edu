// @ts-nocheck
(function () {
  const { useState, useEffect, useRef } = React;
  const Ic = window.Icon;

  function AssignmentDropdown({ courseId, role }) {
    const [open, setOpen] = useState(false);
    const [showModal, setShowModal] = useState(false);
    const ref = useRef(null);

    useEffect(() => {
      if (!open) return;
      function onDocClick(e) {
        if (ref.current && !ref.current.contains(e.target)) setOpen(false);
      }
      document.addEventListener("mousedown", onDocClick);
      return () => document.removeEventListener("mousedown", onDocClick);
    }, [open]);

    const items = [
      {
        key: "assignment",
        label: "Tạo bài tập",
        icon: "clipboard",
        desc: "Bài tập nộp file",
        disabled: false,
        onClick: () => { setOpen(false); setShowModal(true); },
      },
      {
        key: "quiz",
        label: "Tạo bài trắc nghiệm",
        icon: "help_circle",
        desc: "Câu hỏi trắc nghiệm",
        disabled: true,
        badge: "Sắp ra mắt",
        onClick: () => {},
      },
    ];

    return (
      <div ref={ref} style={{ position: "relative" }}>
        {showModal && window.CreateAssignmentModal && (
          React.createElement(window.CreateAssignmentModal, {
            courseId,
            role,
            onClose: (refreshed) => { setShowModal(false); if (refreshed) window.location.reload(); },
          })
        )}
        <button onClick={() => setOpen(p => !p)}
          style={{ display: "inline-flex", alignItems: "center", gap: 5,
            padding: "5px 11px", borderRadius: 8, fontSize: 12, fontWeight: 600,
            cursor: "pointer", border: "1.5px solid #2563eb",
            background: "#2563eb", color: "#fff",
            transition: ".12s" }}
          onMouseEnter={e => { e.currentTarget.style.background = "#1d4ed8"; }}
          onMouseLeave={e => { e.currentTarget.style.background = "#2563eb"; }}>
          <Ic n="plus" size={13} />
          Tạo bài tập
          <Ic n="chevron_down" size={11} style={{
            transform: open ? "rotate(180deg)" : "none", transition: ".2s",
          }} />
        </button>

        {open && (
          <div style={{ position: "absolute", top: "100%", right: 0, zIndex: 50,
            marginTop: 4, minWidth: 220, background: "#fff", borderRadius: 10,
            border: "1px solid #e2e8f0", boxShadow: "0 8px 24px rgba(0,0,0,.1)",
            padding: "4px", overflow: "hidden" }}>
            {items.map(item => {
              const isDisabled = item.disabled;
              return (
                <button key={item.key} onClick={item.onClick}
                  disabled={isDisabled}
                  style={{ display: "flex", alignItems: "center", gap: 9, width: "100%",
                    padding: "8px 10px", borderRadius: 8, border: "none",
                    background: "transparent", cursor: isDisabled ? "not-allowed" : "pointer",
                    opacity: isDisabled ? 0.55 : 1,
                    textAlign: "left", transition: ".1s" }}
                  onMouseEnter={e => { if (!isDisabled) e.currentTarget.style.background = "#f8fafc"; }}
                  onMouseLeave={e => { e.currentTarget.style.background = "transparent"; }}>
                  <div style={{ width: 28, height: 28, borderRadius: 7, flexShrink: 0,
                    background: isDisabled ? "#f1f5f9" : "#eff6ff",
                    color: isDisabled ? "#94a3b8" : "#2563eb",
                    display: "grid", placeItems: "center" }}>
                    <Ic n={item.icon} size={13} />
                  </div>
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{ fontSize: 12.5, fontWeight: 600, color: "#0f172a" }}>
                      {item.label}
                    </div>
                    <div style={{ fontSize: 10.5, color: "#94a3b8" }}>
                      {item.desc}
                    </div>
                  </div>
                  {item.badge && (
                    <span style={{ fontSize: 9, fontWeight: 700, borderRadius: 4,
                      padding: "2px 5px", background: "#fef9c3", color: "#92400e",
                      whiteSpace: "nowrap", flexShrink: 0 }}>
                      {item.badge}
                    </span>
                  )}
                </button>
              );
            })}
          </div>
        )}
      </div>
    );
  }

  Object.assign(window, { AssignmentDropdown });
})();
