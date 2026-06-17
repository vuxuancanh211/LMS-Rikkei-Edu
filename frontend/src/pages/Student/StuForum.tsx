// @ts-nocheck
/* ============================================================
   RIKKEI EDU — Diễn đàn (+ popup tạo bài, trang chi tiết & bình luận)
   ============================================================ */
(function () {
const { useState: uS } = React;
const Ic = window.Icon;
const D = window.DATA;
const { Avatar:Av, Status:St, Progress:Pg, StatCard:SC, CourseCard:CC, Search:Se, Tabs:Tb, Select:Sl, Section:Sn, Pager:Pgr, Modal:Md, ModalHead:MH, Empty:Em, LineChart:LC, BarChart:BC, Donut:Dn } = window;

const FORUM_TAGS = { "Hỏi đáp":"info","Thảo luận":"neutral","Tài liệu":"success" };

function ForumDetail({ post, onBack }) {
  const th = D.forumThread;
  const [reply, setReply] = uS(null); // comment id being replied to
  return (
    <div className="page fade-in" style={{ maxWidth: 880 }}>
      <div className="row gap-10" style={{ marginBottom: 16, cursor: "pointer", color: "var(--text-2)" }} onClick={onBack}><Ic n="arrow_left" size={18} /><span style={{ fontWeight: 600 }}>Quay lại diễn đàn</span></div>

      {/* Post */}
      <div className="card card-pad" style={{ marginBottom: 18 }}>
        <div className="row gap-8 wrap" style={{ marginBottom: 12 }}>
          {post.pinned && <span className="chip chip-warning"><Ic n="pin" size={12}/>Ghim</span>}
          <span className={"chip chip-" + (FORUM_TAGS[post.tag]||"neutral")}>{post.tag}</span>
          <span className="t-xs dim">• {post.course}</span>
        </div>
        <h1 className="t-h2" style={{ margin: "0 0 14px", lineHeight: 1.3 }}>{post.title}</h1>
        <div className="row gap-12" style={{ marginBottom: 18 }}>
          <Av name={post.author} size={44} />
          <div className="grow"><div style={{ fontWeight: 700, fontSize: 14.5 }}>{post.author}</div><div className="t-xs muted">{post.time} • {th.views} lượt xem</div></div>
          <button className="btn btn-ghost btn-sm"><Ic n="bell" size={15} />Theo dõi</button>
        </div>
        <div style={{ fontSize: 15, lineHeight: 1.7, color: "var(--text)", whiteSpace: "pre-line" }}>{th.body}</div>
        <div className="row gap-10" style={{ marginTop: 20, paddingTop: 16, borderTop: "1px solid var(--border)" }}>
          <button className="btn btn-soft btn-sm"><Ic n="thumbs_up" size={15} />Hữu ích ({th.likes})</button>
          <button className="btn btn-ghost btn-sm"><Ic n="message" size={15} />{post.replies} trả lời</button>
          <button className="btn btn-ghost btn-sm"><Ic n="send" size={15} />Chia sẻ</button>
        </div>
      </div>

      {/* Reply box */}
      <div className="card card-pad" style={{ marginBottom: 18 }}>
        <div className="row gap-12" style={{ alignItems: "flex-start" }}>
          <Av name="Hoàng Văn Em" size={40} />
          <div className="grow">
            <textarea className="input" style={{ height: 84, padding: 12, resize: "none" }} placeholder="Viết câu trả lời của bạn..." />
            <div className="between" style={{ marginTop: 10 }}>
              <button className="btn btn-ghost btn-sm"><Ic n="upload" size={15} />Đính kèm</button>
              <button className="btn btn-primary btn-sm"><Ic n="send" size={15} />Gửi trả lời</button>
            </div>
          </div>
        </div>
      </div>

      {/* Comments */}
      <div className="between" style={{ marginBottom: 14 }}>
        <h3 className="t-h3">{th.comments.length} câu trả lời</h3>
        <Sl value="best" onChange={()=>{}} options={[{v:"best",label:"Hữu ích nhất"},{v:"new",label:"Mới nhất"}]} style={{ width: 160 }} />
      </div>

      <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
        {th.comments.map(cm => (
          <div key={cm.id} className="card card-pad" style={cm.best ? { borderColor: "var(--success)", boxShadow: "0 0 0 1px var(--success)" } : null}>
            {cm.best && <div className="chip chip-success" style={{ marginBottom: 12 }}><Ic n="check_circle" size={13} />Câu trả lời hữu ích nhất</div>}
            <div className="row gap-12" style={{ alignItems: "flex-start" }}>
              <Av name={cm.author} size={40} />
              <div className="grow" style={{ minWidth: 0 }}>
                <div className="row gap-8" style={{ marginBottom: 6 }}>
                  <b style={{ fontSize: 14 }}>{cm.author}</b>
                  {cm.role === "GV" && <span className="chip chip-info" style={{ fontSize: 10.5, padding: "1px 8px" }}>Giảng viên</span>}
                  <span className="t-xs dim">• {cm.time}</span>
                </div>
                <div style={{ fontSize: 14.5, lineHeight: 1.65, whiteSpace: "pre-line" }}>{cm.text}</div>
                <div className="row gap-8" style={{ marginTop: 10 }}>
                  <button className="btn btn-ghost btn-sm" style={{ height: 32 }}><Ic n="thumbs_up" size={14} />{cm.likes}</button>
                  <button className="btn btn-ghost btn-sm" style={{ height: 32 }} onClick={() => setReply(reply === cm.id ? null : cm.id)}><Ic n="message" size={14} />Trả lời</button>
                </div>

                {/* nested replies */}
                {cm.replies.length > 0 && (
                  <div style={{ marginTop: 14, paddingLeft: 16, borderLeft: "2px solid var(--border)", display: "flex", flexDirection: "column", gap: 14 }}>
                    {cm.replies.map(rp => (
                      <div key={rp.id} className="row gap-10" style={{ alignItems: "flex-start" }}>
                        <Av name={rp.author} size={32} />
                        <div className="grow" style={{ minWidth: 0 }}>
                          <div className="row gap-8" style={{ marginBottom: 4 }}><b style={{ fontSize: 13.5 }}>{rp.author}</b>{rp.role === "GV" && <span className="chip chip-info" style={{ fontSize: 10, padding: "1px 7px" }}>Giảng viên</span>}<span className="t-xs dim">• {rp.time}</span></div>
                          <div style={{ fontSize: 14, lineHeight: 1.6 }}>{rp.text}</div>
                          <button className="btn btn-ghost btn-sm" style={{ height: 30, marginTop: 6 }}><Ic n="thumbs_up" size={13} />{rp.likes}</button>
                        </div>
                      </div>
                    ))}
                  </div>
                )}

                {/* inline reply input */}
                {reply === cm.id && (
                  <div className="row gap-10" style={{ marginTop: 12, alignItems: "flex-start" }}>
                    <Av name="Hoàng Văn Em" size={32} />
                    <div className="grow">
                      <input className="input" placeholder={"Trả lời " + cm.author + "..."} autoFocus />
                      <div className="row gap-8" style={{ marginTop: 8, justifyContent: "flex-end" }}><button className="btn btn-ghost btn-sm" onClick={() => setReply(null)}>Hủy</button><button className="btn btn-primary btn-sm"><Ic n="send" size={14} />Gửi</button></div>
                    </div>
                  </div>
                )}
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

function StuForum({ demo }) {
  const [q, setQ] = uS("");
  const [open, setOpen] = uS(demo === "detail");      // detail view
  const [create, setCreate] = uS(demo === "create");  // create modal
  let list = D.forum.filter(f => !q || f.title.toLowerCase().includes(q.toLowerCase()));
  const pg = window.usePaged(list, 10);
  const tagColor = FORUM_TAGS;

  if (open) return <ForumDetail post={D.forum[0]} onBack={() => setOpen(false)} />;

  return (
    <div className="page fade-in">
      <div className="page-head between"><div><h1 className="t-h1">Diễn đàn thảo luận</h1><p>Đặt câu hỏi, chia sẻ kiến thức cùng học viên trong khóa học.</p></div><button className="btn btn-primary" onClick={() => setCreate(true)}><Ic n="plus" size={17}/>Tạo bài viết</button></div>
      <div className="toolbar"><Se placeholder="Tìm kiếm chủ đề..." value={q} onChange={setQ} /><Sl value="all" onChange={()=>{}} options={[{v:"all",label:"Tất cả khóa học"},{v:"react",label:"ReactJS Nâng cao"}]} style={{width:200,flex:"none"}}/><Sl value="new" onChange={()=>{}} options={[{v:"new",label:"Mới nhất"},{v:"hot",label:"Nhiều trả lời"}]} style={{width:160,flex:"none"}}/></div>
      <Sn pad={false}>
        <div style={{ padding: 8 }}>
          {pg.slice.map(f => (
            <div key={f.id} className="row gap-16" style={{ padding: 16, borderRadius: 13, cursor: "pointer", borderBottom: "1px solid var(--border)" }} onClick={() => setOpen(true)}>
              <Av name={f.author} size={46} />
              <div className="grow" style={{ minWidth: 0 }}>
                <div className="row gap-8 wrap" style={{ marginBottom: 5 }}>
                  {f.pinned && <span className="chip chip-warning"><Ic n="pin" size={12}/>Ghim</span>}
                  <span className={"chip chip-" + (tagColor[f.tag]||"neutral")}>{f.tag}</span>
                  <span className="t-xs dim">• {f.course}</span>
                </div>
                <div style={{ fontWeight: 700, fontSize: 15.5 }} className="truncate">{f.title}</div>
                <div className="t-sm muted row gap-12" style={{ marginTop: 5 }}><span>{f.author}</span><span>•</span><span>{f.time}</span></div>
              </div>
              <div className="row gap-20" style={{ flex: "none" }}>
                <div style={{ textAlign: "center" }}><div style={{ fontWeight: 800, fontSize: 17 }}>{f.replies}</div><div className="t-xs dim">trả lời</div></div>
                <div style={{ textAlign: "center" }}><div style={{ fontWeight: 800, fontSize: 17 }}>{f.views}</div><div className="t-xs dim">lượt xem</div></div>
              </div>
            </div>
          ))}
        </div>
      </Sn>
      <window.PageBar pg={pg} unit="chủ đề" />

      {/* Create post modal */}
      <Md open={create} onClose={() => setCreate(false)} max={620}>
        <MH title="Tạo bài viết mới" sub="Chia sẻ câu hỏi hoặc kiến thức với cộng đồng" icon="message" iconBg="#eaf1ff" iconColor="#2563eb" onClose={() => setCreate(false)} />
        <div className="modal-body" style={{ display: "flex", flexDirection: "column", gap: 16 }}>
          <div><label className="t-label" style={{ display: "block", marginBottom: 7 }}>Tiêu đề</label><input className="input" placeholder="VD: Sự khác nhau giữa useMemo và useCallback?" /></div>
          <div className="grid grid-2" style={{ gap: 14 }}>
            <div><label className="t-label" style={{ display: "block", marginBottom: 7 }}>Khóa học</label><Sl value={D.courses[0].title} onChange={()=>{}} options={D.courses.map(c=>({v:c.title,label:c.title}))} /></div>
            <div><label className="t-label" style={{ display: "block", marginBottom: 7 }}>Chủ đề</label><Sl value="Hỏi đáp" onChange={()=>{}} options={[{v:"Hỏi đáp",label:"Hỏi đáp"},{v:"Thảo luận",label:"Thảo luận"},{v:"Tài liệu",label:"Tài liệu"}]} /></div>
          </div>
          <div><label className="t-label" style={{ display: "block", marginBottom: 7 }}>Nội dung</label><textarea className="input" style={{ height: 130, padding: 12, resize: "none" }} placeholder="Mô tả chi tiết câu hỏi hoặc nội dung bạn muốn chia sẻ..." /></div>
          <div style={{ border: "2px dashed var(--border-strong)", borderRadius: 12, padding: 20, textAlign: "center", color: "var(--text-3)" }}><Ic n="upload" size={24} style={{ marginBottom: 6 }} /><div className="t-sm">Đính kèm hình ảnh hoặc tệp (tùy chọn)</div></div>
        </div>
        <div className="modal-foot"><button className="btn btn-ghost" onClick={() => setCreate(false)}>Hủy</button><button className="btn btn-primary" onClick={() => setCreate(false)}><Ic n="send" size={16} />Đăng bài</button></div>
      </Md>
    </div>
  );
}

window.StuForum = StuForum;
})();
