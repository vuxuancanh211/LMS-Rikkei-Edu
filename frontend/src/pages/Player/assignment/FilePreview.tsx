// @ts-nocheck
import { createPortal } from 'react-dom';
(function () {
  const { useState, useEffect, useRef } = React;
  const Ic = window.Icon;
  const mammoth = window.mammoth;

  function FilePreview({ files, initialIdx, onClose }) {
    const [idx, setIdx] = useState(initialIdx);
    const [docxHtml, setDocxHtml] = useState({});
    const [previewLoading, setPreviewLoading] = useState(false);
    const [previewError, setPreviewError] = useState("");
    const docxCache = useRef({});
    const objectUrls = useRef(new Map());
    const isOpen = useRef(true);

    const file = files[idx];
    const hasPrev = idx > 0;
    const hasNext = idx < files.length - 1;

    function getUrl(item) {
      if (item.url) return item.url;
      if (item.file) {
        if (!objectUrls.current.has(item)) {
          objectUrls.current.set(item, URL.createObjectURL(item.file));
        }
        return objectUrls.current.get(item);
      }
      return null;
    }

    function isDocx(item) {
      return item.name.toLowerCase().endsWith(".docx")
        || item.mimeType === "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    }

    function guessMime(item) {
      return item.mimeType || (item.file && item.file.type) || "";
    }

    function isImage(item) { return guessMime(item).startsWith("image/"); }
    function isPdf(item) { return guessMime(item) === "application/pdf"; }

    useEffect(() => {
      if (!file || !isDocx(file)) {
        setPreviewLoading(false);
        setPreviewError("");
        return;
      }
      if (docxCache.current[file.name]) {
        setPreviewLoading(false);
        setPreviewError("");
        return;
      }
      setPreviewLoading(true);
      setPreviewError("");

      async function load() {
        try {
          let buf;
          if (file.file) {
            buf = await file.file.arrayBuffer();
          } else if (file.url) {
            const res = await fetch(file.url);
            buf = await res.arrayBuffer();
          }
          if (!isOpen.current) return;
          if (buf) {
            const result = await mammoth.convertToHtml({ arrayBuffer: buf });
            docxCache.current[file.name] = result.value;
            setDocxHtml(prev => ({ ...prev, [file.name]: result.value }));
          }
          setPreviewLoading(false);
        } catch (e) {
          if (isOpen.current) {
            setPreviewError("Không thể đọc file DOCX");
            setPreviewLoading(false);
          }
        }
      }
      load();
    }, [idx]);

    useEffect(() => {
      function handleKey(e) {
        if (e.key === "Escape") { onClose(); return; }
        if (e.key === "ArrowLeft" && hasPrev) { e.preventDefault(); setIdx(idx - 1); }
        if (e.key === "ArrowRight" && hasNext) { e.preventDefault(); setIdx(idx + 1); }
      }
      window.addEventListener("keydown", handleKey);
      return () => window.removeEventListener("keydown", handleKey);
    }, [idx, hasPrev, hasNext, onClose]);

    useEffect(() => {
      isOpen.current = true;
      return () => {
        isOpen.current = false;
        for (const url of objectUrls.current.values()) {
          URL.revokeObjectURL(url);
        }
        objectUrls.current.clear();
      };
    }, []);

    if (!file) return null;

    const srcUrl = getUrl(file);
    const fileSizeStr = file.size ? (file.size / 1024).toFixed(0) + " KB" : "";

    let content;
    if (isImage(file)) {
      content = <img src={srcUrl}
        style={{ maxWidth: "100%", maxHeight: "100%", minWidth: 400, minHeight: 300,
          objectFit: "contain", borderRadius: 4 }} />;
    } else if (isPdf(file)) {
      content = <iframe src={srcUrl}
        style={{ width: "100%", height: "100%", border: "none", borderRadius: 4,
          minHeight: "60vh" }} />;
    } else if (isDocx(file)) {
      if (previewLoading) {
        content = <div style={{ color: "#94a3b8", fontSize: 13, textAlign: "center" }}>
          <div style={{ fontSize: 11, marginBottom: 8 }}>⏳</div>
          Đang tải nội dung...
        </div>;
      } else if (previewError) {
        content = <div style={{ color: "#f87171", fontSize: 13, textAlign: "center" }}>
          <Ic n="alert_circle" size={20} style={{ marginBottom: 6 }} /><br />
          {previewError}
        </div>;
      } else if (docxHtml[file.name]) {
        content = <div dangerouslySetInnerHTML={{ __html: docxHtml[file.name] }}
          style={{ width: "100%", height: "100%", overflow: "auto", background: "#fff",
            padding: 32, borderRadius: 6, color: "#0f172a", fontSize: 14,
            lineHeight: 1.7, boxSizing: "border-box" }} />;
      } else {
        content = <div style={{ color: "#94a3b8", fontSize: 13, textAlign: "center" }}>
          Không thể xem trước
        </div>;
      }
    } else {
      content = <div style={{ color: "#94a3b8", fontSize: 13, textAlign: "center" }}>
        <Ic n="file" size={28} style={{ marginBottom: 6 }} /><br />
        Không thể xem trước file này
      </div>;
    }

    return createPortal(
      <div style={{ position: "fixed", top: 0, left: 0, right: 0, bottom: 0, zIndex: 400,
        background: "rgba(0,0,0,.88)", display: "flex", flexDirection: "column" }}>

        {/* Top bar */}
        <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between",
          padding: "10px 16px", flexShrink: 0 }}>
          <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
            <button onClick={onClose}
              style={{ border: "none", background: "rgba(255,255,255,.1)", color: "#fff",
                width: 32, height: 32, borderRadius: 8, cursor: "pointer", fontSize: 14,
                display: "grid", placeItems: "center" }}>
              ✕
            </button>
            <Ic n="file" size={13} style={{ color: "#94a3b8" }} />
            <span style={{ color: "#f1f5f9", fontSize: 13, fontWeight: 500 }}>{file.name}</span>
            {fileSizeStr && <span style={{ color: "#64748b", fontSize: 11 }}>({fileSizeStr})</span>}
          </div>
          <span style={{ color: "#94a3b8", fontSize: 12 }}>
            {idx + 1} / {files.length}
          </span>
        </div>

        {/* Content area */}
        <div style={{ flex: 1, position: "relative", overflow: "hidden" }}>
          {hasPrev && (
            <button onClick={() => setIdx(idx - 1)}
              style={{ position: "absolute", left: 16, top: "50%", translate: "0 -50%",
                zIndex: 10, border: "none", background: "rgba(255,255,255,.1)",
                color: "#fff", width: 44, height: 44, borderRadius: "50%",
                cursor: "pointer", fontSize: 20, display: "grid", placeItems: "center" }}>
              ‹
            </button>
          )}
          <div style={{ width: "100%", height: "100%", display: "flex",
            alignItems: "center", justifyContent: "center", padding: "16px 64px",
            boxSizing: "border-box" }}>
            {content}
          </div>
          {hasNext && (
            <button onClick={() => setIdx(idx + 1)}
              style={{ position: "absolute", right: 16, top: "50%", translate: "0 -50%",
                zIndex: 10, border: "none", background: "rgba(255,255,255,.1)",
                color: "#fff", width: 44, height: 44, borderRadius: "50%",
                cursor: "pointer", fontSize: 20, display: "grid", placeItems: "center" }}>
              ›
            </button>
          )}
        </div>
      </div>,
      document.body
    );
  }

  Object.assign(window, { FilePreview });
})();
