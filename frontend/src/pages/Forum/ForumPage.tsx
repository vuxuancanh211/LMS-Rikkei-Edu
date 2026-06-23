// @ts-nocheck
import React, { useEffect, useState } from 'react';
import {
  createForumPost,
  createForumReply,
  deleteForumPost,
  deleteForumReply,
  getForumCourses,
  getForumPostDetail,
  getForumPosts,
  togglePinPost,
  toggleUpvotePost,
  toggleUpvoteReply,
  reportPost,
  reportReply,
  updateForumPost,
  updateForumReply,
  type ForumCourse,
  type ForumPost,
  type ForumPostDetail,
} from '../../services';
import { useAuthStore } from '../../store';

function formatTime(value?: string) {
  if (!value) return '';
  return new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value));
}

function roleLabel(role?: string) {
  if (role === 'INSTRUCTOR') return 'Giảng viên';
  if (role === 'ADMIN') return 'Admin';
  return 'Học viên';
}

const TOPIC_MAP = {
  'announcement': { label: '📢 Thông báo', cls: 'chip-warning' },
  'qa': { label: '💬 Hỏi đáp', cls: 'chip-info' },
  'share': { label: '📝 Chia sẻ', cls: 'chip-success' },
  'idea': { label: '💡 Ý tưởng', cls: 'chip-neutral' },
  'discussion': { label: '🤝 Thảo luận', cls: 'chip-neutral' },
};

const topicOptions = [{ v: '', label: 'Chọn chủ đề' }, ...Object.entries(TOPIC_MAP).map(([v, t]) => ({ v, label: t.label }))];

function topicInfo(value?: string | null) {
  if (!value) return null;
  return TOPIC_MAP[value] || null;
}

function appendReplyToTree(replies, parentReplyId, newReply) {
  if (!parentReplyId) return [...replies, { ...newReply, replies: newReply.replies || [] }];

  return replies.map((reply) => {
    if (reply.id === parentReplyId) {
      return { ...reply, replies: [...(reply.replies || []), { ...newReply, replies: newReply.replies || [] }] };
    }

    return { ...reply, replies: appendReplyToTree(reply.replies || [], parentReplyId, newReply) };
  });
}

function updateReplyInTree(replies, updatedReply) {
  return replies.map((reply) => {
    if (reply.id === updatedReply.id) {
      return { ...reply, ...updatedReply, replies: reply.replies || [] };
    }

    return { ...reply, replies: updateReplyInTree(reply.replies || [], updatedReply) };
  });
}

function removeReplyFromTree(replies, replyId) {
  return replies
    .filter((reply) => reply.id !== replyId)
    .map((reply) => ({ ...reply, replies: removeReplyFromTree(reply.replies || [], replyId) }));
}

function ForumDetail({ detail, setDetail, loading, error, onBack }) {
  const Ic = window.Icon;
  const Av = window.Avatar;
  const Sl = window.Select;
  const Md = window.Modal;
  const MH = window.ModalHead;
  const currentUser = useAuthStore((state) => state.user);
  const role = useAuthStore((state) => state.role);
  const [replyContent, setReplyContent] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [editPostOpen, setEditPostOpen] = useState(false);
  const [editTitle, setEditTitle] = useState('');
  const [editContent, setEditContent] = useState('');
  const [editPinned, setEditPinned] = useState(false);
  const [editTopic, setEditTopic] = useState('');
  const [editingReplyId, setEditingReplyId] = useState<string | null>(null);
  const [editingReplyContent, setEditingReplyContent] = useState('');
  const [replyTarget, setReplyTarget] = useState(null);
  const [nestedReplyContent, setNestedReplyContent] = useState('');
  const [deleteTarget, setDeleteTarget] = useState<{ type: 'post' | 'reply'; id: string } | null>(null);
  const [reportTarget, setReportTarget] = useState<{ type: 'post' | 'reply'; id: string } | null>(null);
  const [reportReason, setReportReason] = useState('');
  const [reportDescription, setReportDescription] = useState('');

  useEffect(() => {
    if (!detail?.post) return;
    setEditTitle(detail.post.title);
    setEditContent(detail.post.content);
    setEditPinned(detail.post.pinned);
    setEditTopic(detail.post.topic || '');
  }, [detail?.post?.id]);

  if (loading) {
    return <div className="page fade-in"><div className="card card-pad t-sm muted">Đang tải bài viết...</div></div>;
  }

  if (error || !detail) {
    return <div className="page fade-in"><button className="btn btn-ghost" onClick={onBack}>Quay lại</button><div className="card card-pad" style={{ marginTop: 16 }}>{error || 'Không tìm thấy bài viết'}</div></div>;
  }

  const post = detail.post;
  const canDeletePost = currentUser?.id === post.author.id;
  const canEditPost = canDeletePost;

  const handleSubmitReply = async () => {
    if (!replyContent.trim()) return;
    setSubmitting(true);
    try {
      const createdReply = await createForumReply(post.id, { content: replyContent.trim() });
      setReplyContent('');
      setDetail((current) => current ? ({
        ...current,
        post: { ...current.post, replyCount: current.post.replyCount + 1 },
        replies: appendReplyToTree(current.replies || [], null, createdReply),
      }) : current);
    } finally {
      setSubmitting(false);
    }
  };

  const handleDeletePost = async () => {
    try {
      await deleteForumPost(post.id);
      onBack(true);
    } catch {
      // delete failed — stay on page
    }
  };

  const handleTogglePin = async () => {
    if (submitting) return;
    setSubmitting(true);
    try {
      const updatedPost = await togglePinPost(post.id);
      setDetail((current) => current ? ({ ...current, post: updatedPost }) : current);
    } finally {
      setSubmitting(false);
    }
  };

  const handleDeleteReply = async (replyId: string) => {
    try {
      await deleteForumReply(replyId);
      setDetail((current) => current ? ({
        ...current,
        post: { ...current.post, replyCount: Math.max(0, current.post.replyCount - 1) },
        replies: removeReplyFromTree(current.replies || [], replyId),
      }) : current);
    } catch {
      // delete failed — stay in place
    }
  };

  const handleToggleUpvote = async () => {
    if (submitting) return;
    setSubmitting(true);
    try {
      const updatedPost = await toggleUpvotePost(post.id);
      setDetail((current) => current ? ({ ...current, post: updatedPost }) : current);
    } finally {
      setSubmitting(false);
    }
  };

  const handleToggleReplyUpvote = async (replyId: string) => {
    if (submitting) return;
    setSubmitting(true);
    try {
      const updatedReply = await toggleUpvoteReply(replyId);
      setDetail((current) => current ? ({
        ...current,
        replies: updateReplyInTree(current.replies || [], updatedReply),
      }) : current);
    } finally {
      setSubmitting(false);
    }
  };

  const handleUpdatePost = async () => {
    if (!editTitle.trim() || !editContent.trim()) return;
    setSubmitting(true);
    try {
      const updatedPost = await updateForumPost(post.id, { topic: editTopic || null, title: editTitle.trim(), content: editContent.trim(), pinned: editPinned });
      setEditPostOpen(false);
      setDetail((current) => current ? ({ ...current, post: updatedPost }) : current);
    } finally {
      setSubmitting(false);
    }
  };

  const startEditReply = (reply) => {
    setEditingReplyId(reply.id);
    setEditingReplyContent(reply.content);
  };

  const handleUpdateReply = async () => {
    if (!editingReplyId || !editingReplyContent.trim()) return;
    setSubmitting(true);
    try {
      const updatedReply = await updateForumReply(editingReplyId, { content: editingReplyContent.trim() });
      setEditingReplyId(null);
      setEditingReplyContent('');
      setDetail((current) => current ? ({
        ...current,
        replies: updateReplyInTree(current.replies || [], updatedReply),
      }) : current);
    } finally {
      setSubmitting(false);
    }
  };

  const startNestedReply = (reply) => {
    setReplyTarget(reply);
    setNestedReplyContent('');
    setEditingReplyId(null);
  };

  const handleSubmitNestedReply = async () => {
    if (!replyTarget || !nestedReplyContent.trim()) return;
    setSubmitting(true);
    try {
      const createdReply = await createForumReply(post.id, { content: nestedReplyContent.trim(), parentReplyId: replyTarget.id });
      setReplyTarget(null);
      setNestedReplyContent('');
      setDetail((current) => current ? ({
        ...current,
        post: { ...current.post, replyCount: current.post.replyCount + 1 },
        replies: appendReplyToTree(current.replies || [], replyTarget.id, createdReply),
      }) : current);
    } finally {
      setSubmitting(false);
    }
  };

  const confirmDelete = async () => {
    if (!deleteTarget) return;
    const target = deleteTarget;
    setDeleteTarget(null);
    if (target.type === 'post') {
      await handleDeletePost();
    } else {
      await handleDeleteReply(target.id);
    }
  };

  const handleReportSubmit = async () => {
    if (!reportTarget || !reportReason) return;
    setSubmitting(true);
    try {
      if (reportTarget.type === 'post') {
        await reportPost(reportTarget.id, { reason: reportReason, description: reportDescription || undefined });
      } else {
        await reportReply(reportTarget.id, { reason: reportReason, description: reportDescription || undefined });
      }
      setReportTarget(null);
      setReportReason('');
      setReportDescription('');
    } catch {
      // report failed — modal stays open
    } finally {
      setSubmitting(false);
    }
  };

  const renderReply = (reply) => {
    const canEditReply = currentUser?.id === reply.author.id;
    const canReply = (reply.depth || 1) < 3;
    const childReplies = reply.replies || [];

    return (
      <div key={reply.id} className={reply.depth === 1 ? 'card card-pad' : ''} style={reply.depth === 1 ? null : { paddingLeft: 18, borderLeft: '2px solid var(--border)', marginTop: 12 }}>
        <div className="row gap-12" style={{ alignItems: 'flex-start' }}>
          <Av name={reply.author.fullName} size={reply.depth === 1 ? 40 : 32} />
          <div className="grow" style={{ minWidth: 0 }}>
            <div className="between gap-8" style={{ marginBottom: 6 }}>
              <div className="row gap-8 wrap"><b style={{ fontSize: reply.depth === 1 ? 14 : 13.5 }}>{reply.author.fullName}</b>{reply.author.role === 'INSTRUCTOR' && <span className="chip chip-info" style={{ fontSize: 10.5, padding: '1px 8px' }}>Giảng viên</span>}<span className="t-xs dim">• {formatTime(reply.createdAt)}</span></div>
              <div className="row gap-6">
                <button className="btn-ghost" onClick={() => handleToggleReplyUpvote(reply.id)} style={{ display: 'inline-flex', alignItems: 'center', gap: 3, padding: '4px 8px', border: 'none', borderRadius: 6, fontSize: 12, cursor: 'pointer', color: reply.upvoted ? 'var(--primary)' : 'var(--text-3)', background: 'transparent' }}><Ic n="thumbs_up" size={12} />{reply.upvoteCount}</button>
                {canReply && <button className="icon-btn" style={{ width: 34, height: 34 }} onClick={() => startNestedReply(reply)} title="Trả lời"><Ic n="message" size={16} /></button>}
                <button className="icon-btn" style={{ width: 34, height: 34 }} onClick={() => { setReportTarget({ type: 'reply', id: reply.id }); setReportReason(''); setReportDescription(''); }} title="Báo cáo"><Ic n="flag" size={16} /></button>
                {canEditReply && <><button className="icon-btn" style={{ width: 34, height: 34 }} onClick={() => startEditReply(reply)} title="Sửa"><Ic n="edit" size={16} /></button><button className="icon-btn" style={{ width: 34, height: 34, color: 'var(--error)' }} onClick={() => setDeleteTarget({ type: 'reply', id: reply.id })} title="Xóa"><Ic n="trash" size={16} /></button></>}
              </div>
            </div>

            {editingReplyId === reply.id ? (
              <div style={{ marginTop: 10 }}>
                <textarea className="input" style={{ height: 86, padding: 12, resize: 'none' }} value={editingReplyContent} onChange={(event) => setEditingReplyContent(event.target.value)} autoFocus />
                <div className="row gap-8" style={{ justifyContent: 'flex-end', marginTop: 8 }}><button className="btn btn-ghost btn-sm" onClick={() => setEditingReplyId(null)}>Hủy</button><button className="btn btn-primary btn-sm" disabled={submitting || !editingReplyContent.trim()} onClick={handleUpdateReply}>Lưu</button></div>
              </div>
            ) : <div style={{ fontSize: 14.5, lineHeight: 1.65, whiteSpace: 'pre-line' }}>{reply.content}</div>}

            {replyTarget?.id === reply.id && (
              <div className="row gap-10" style={{ marginTop: 12, alignItems: 'flex-start' }}>
                <Av name={currentUser?.fullName || 'User'} size={30} />
                <div className="grow">
                  <textarea className="input" style={{ height: 86, padding: 12, resize: 'none' }} placeholder={`Trả lời ${reply.author.fullName}...`} value={nestedReplyContent} onChange={(event) => setNestedReplyContent(event.target.value)} autoFocus />
                  <div className="row gap-8" style={{ justifyContent: 'flex-end', marginTop: 8 }}><button className="btn btn-ghost btn-sm" onClick={() => setReplyTarget(null)}>Hủy</button><button className="btn btn-primary btn-sm" disabled={submitting || !nestedReplyContent.trim()} onClick={handleSubmitNestedReply}>Gửi</button></div>
                </div>
              </div>
            )}

            {childReplies.length > 0 && <div style={{ marginTop: 12 }}>{childReplies.map(renderReply)}</div>}
          </div>
        </div>
      </div>
    );
  };

  return (
    <div className="page fade-in">
      <div className="row gap-10" style={{ marginBottom: 16, cursor: 'pointer', color: 'var(--text-2)' }} onClick={() => onBack(true)}><Ic n="arrow_left" size={18} /><span style={{ fontWeight: 600 }}>Quay lại diễn đàn</span></div>

      <div className="card card-pad" style={{ marginBottom: 18 }}>
          <div className="between gap-12" style={{ marginBottom: 12 }}>
            <div className="row gap-8 wrap">
              {post.pinned && <span className="chip chip-warning"><Ic n="pin" size={12} />Ghim</span>}
              {topicInfo(post.topic) && <span className={`chip ${topicInfo(post.topic).cls}`}>{topicInfo(post.topic).label}</span>}
              <span className="chip chip-info">{post.courseTitle}</span>
            </div>
            <div className="row gap-8">
              {(role === 'instructor' || role === 'admin') && <button className="icon-btn" style={{ width: 34, height: 34, color: post.pinned ? 'var(--warning)' : undefined }} onClick={handleTogglePin} title={post.pinned ? 'Bỏ ghim' : 'Ghim bài viết'}><Ic n="pin" size={16} /></button>}
              {canEditPost && <><button className="icon-btn" style={{ width: 34, height: 34 }} onClick={() => setEditPostOpen(true)} title="Sửa"><Ic n="edit" size={16} /></button><button className="icon-btn" style={{ width: 34, height: 34, color: 'var(--error)' }} onClick={() => setDeleteTarget({ type: 'post', id: post.id })} title="Xóa"><Ic n="trash" size={16} /></button></>}
            </div>
          </div>
        <h1 className="t-h2" style={{ margin: '0 0 14px', lineHeight: 1.3 }}>{post.title}</h1>
        <div className="row gap-12" style={{ marginBottom: 18 }}>
          <Av name={post.author.fullName} size={44} />
          <div className="grow"><div style={{ fontWeight: 700, fontSize: 14.5 }}>{post.author.fullName}</div><div className="t-xs muted">{roleLabel(post.author.role)} • {formatTime(post.createdAt)}</div></div>
        </div>
        <div style={{ fontSize: 15, lineHeight: 1.7, color: 'var(--text)', whiteSpace: 'pre-line' }}>{post.content}</div>
        <div className="row gap-10" style={{ marginTop: 20, paddingTop: 16, borderTop: '1px solid var(--border)' }}>
          <button className="btn btn-ghost btn-sm" onClick={handleToggleUpvote} style={{ color: post.upvoted ? 'var(--primary)' : undefined }}><Ic n="thumbs_up" size={15} />{post.upvoteCount}</button>
          <button className="btn btn-ghost btn-sm"><Ic n="message" size={15} />{post.replyCount} trả lời</button>
          <button className="btn btn-ghost btn-sm" onClick={() => { setReportTarget({ type: 'post', id: post.id }); setReportReason(''); setReportDescription(''); }}><Ic n="flag" size={15} />Báo cáo</button>
        </div>
      </div>

      <div className="card card-pad" style={{ marginBottom: 18 }}>
        <div className="row gap-12" style={{ alignItems: 'flex-start' }}>
          <Av name={currentUser?.fullName || 'User'} size={40} />
          <div className="grow">
            <textarea className="input" style={{ height: 84, padding: 12, resize: 'none' }} placeholder="Viết câu trả lời của bạn..." value={replyContent} onChange={(event) => setReplyContent(event.target.value)} />
            <div className="between" style={{ marginTop: 10 }}>
              <button className="btn btn-primary btn-sm" disabled={submitting || !replyContent.trim()} onClick={handleSubmitReply}><Ic n="send" size={15} />Gửi trả lời</button>
            </div>
          </div>
        </div>
      </div>

      <div className="between" style={{ marginBottom: 14 }}>
        <h3 className="t-h3">{post.replyCount} câu trả lời</h3>
      </div>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
        {detail.replies.length === 0 && <div className="card card-pad" style={{ textAlign: 'center', color: 'var(--text-2)' }}><Ic n="message" size={28} style={{ marginBottom: 8, color: 'var(--text-3)' }} /><div style={{ fontWeight: 700, color: 'var(--text)' }}>Chưa có câu trả lời</div><div className="t-sm muted" style={{ marginTop: 4 }}>Hãy là người đầu tiên phản hồi chủ đề này.</div></div>}
        {detail.replies.map(renderReply)}
      </div>

      <Md open={editPostOpen} onClose={() => setEditPostOpen(false)} max={620} maxHeight="calc(100vh - 48px)">
        <MH title="Sửa bài viết" sub="Cập nhật nội dung thảo luận trong khóa học" icon="message" iconBg="#eaf1ff" iconColor="#2563eb" onClose={() => setEditPostOpen(false)} />
        <div className="modal-body" style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          <div><label className="t-label" style={{ display: 'block', marginBottom: 7 }}>Chủ đề</label><Sl value={editTopic} onChange={setEditTopic} options={topicOptions} /></div>
          <div><label className="t-label" style={{ display: 'block', marginBottom: 7 }}>Tiêu đề</label><input className="input" value={editTitle} onChange={(event) => setEditTitle(event.target.value)} /></div>
          <div><label className="t-label" style={{ display: 'block', marginBottom: 7 }}>Nội dung</label><textarea className="input" style={{ height: 118, padding: 12, resize: 'none' }} value={editContent} onChange={(event) => setEditContent(event.target.value)} /></div>
          {(role === 'instructor' || role === 'admin') && <label className="row gap-8 t-sm"><input type="checkbox" checked={editPinned} onChange={(event) => setEditPinned(event.target.checked)} />Ghim bài viết</label>}
        </div>
        <div className="modal-foot"><button className="btn btn-ghost" onClick={() => setEditPostOpen(false)}>Hủy</button><button className="btn btn-primary" disabled={submitting || !editTitle.trim() || !editContent.trim()} onClick={handleUpdatePost}>Lưu thay đổi</button></div>
      </Md>

      <Md open={!!deleteTarget} onClose={() => setDeleteTarget(null)} max={460}>
        <MH title="Xác nhận xóa" sub={deleteTarget?.type === 'post' ? 'Bài viết sẽ được ẩn khỏi diễn đàn.' : 'Bình luận sẽ được ẩn khỏi bài viết.'} icon="warn" iconBg="#fff7ed" iconColor="#f97316" onClose={() => setDeleteTarget(null)} />
        <div className="modal-foot"><button className="btn btn-ghost" onClick={() => setDeleteTarget(null)}>Hủy</button><button className="btn btn-danger" onClick={confirmDelete}>Xóa</button></div>
      </Md>

      <Md open={!!reportTarget} onClose={() => setReportTarget(null)} max={520}>
        <MH title="Báo cáo bài viết" sub="Chọn lý do báo cáo nội dung vi phạm" icon="flag" iconBg="#fef5e6" iconColor="#f59e0b" onClose={() => setReportTarget(null)} />
        <div className="modal-body" style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
          <div>
            <label className="t-label" style={{ display: 'block', marginBottom: 7 }}>Lý do</label>
            <Sl value={reportReason} onChange={setReportReason}
              options={[
                { v: '', label: 'Chọn lý do...' },
                { v: 'SPAM', label: 'Spam / Quảng cáo' },
                { v: 'OFFENSIVE', label: 'Nội dung xúc phạm' },
                { v: 'MISINFORMATION', label: 'Thông tin sai lệch' },
                { v: 'OTHER', label: 'Khác' },
              ]} />
          </div>
          <div>
            <label className="t-label" style={{ display: 'block', marginBottom: 7 }}>Mô tả thêm (không bắt buộc)</label>
            <textarea className="input" style={{ height: 86, padding: 12, resize: 'none' }} placeholder="Cung cấp thêm thông tin..." value={reportDescription} onChange={(event) => setReportDescription(event.target.value)} />
          </div>
        </div>
        <div className="modal-foot"><button className="btn btn-ghost" onClick={() => setReportTarget(null)}>Hủy</button><button className="btn btn-primary" disabled={submitting || !reportReason} onClick={handleReportSubmit}>Gửi báo cáo</button></div>
      </Md>
    </div>
  );
}

function ForumPage({ demo }) {
  const Ic = window.Icon;
  const Av = window.Avatar;
  const Se = window.Search;
  const Sl = window.Select;
  const Sn = window.Section;
  const Md = window.Modal;
  const MH = window.ModalHead;
  const role = useAuthStore((state) => state.role);
  const [posts, setPosts] = useState<ForumPost[]>([]);
  const [courses, setCourses] = useState<ForumCourse[]>([]);
  const [detail, setDetail] = useState<ForumPostDetail | null>(null);
  const [selectedPostId, setSelectedPostId] = useState<string | null>(null);
  const [q, setQ] = useState('');
  const [filterTopic, setFilterTopic] = useState('');
  const [courseId, setCourseId] = useState('');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [error, setError] = useState('');
  const [detailError, setDetailError] = useState('');
  const [createOpen, setCreateOpen] = useState(demo === 'create');
  const [createCourseId, setCreateCourseId] = useState('');
  const [createTopic, setCreateTopic] = useState('');
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [pinned, setPinned] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const courseOptions = [{ v: '', label: 'Tất cả khóa học' }, ...courses.map((course) => ({ v: course.id, label: course.title }))];
  const createCourses = courses.filter((course) => course.canCreatePost);
  const createCourseOptions = createCourses.map((course) => ({ v: course.id, label: course.title }));
  const selectedCreateCourse = createCourses.find((course) => course.id === createCourseId);
  const newestIds = new Set([...posts].sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()).slice(0, 3).map(p => p.id));
  const sortedPosts = [...posts].sort((a, b) => {
    if (a.pinned !== b.pinned) return a.pinned ? -1 : 1;
    const aNew = newestIds.has(a.id);
    const bNew = newestIds.has(b.id);
    if (aNew !== bNew) return aNew ? -1 : 1;
    return (b.upvoteCount || 0) - (a.upvoteCount || 0);
  });

  const loadPosts = async () => {
    setLoading(true);
    setError('');
    try {
      const data = await getForumPosts({ courseId: courseId || undefined, keyword: q || undefined, topic: filterTopic || undefined, page, size: 10 });
      setPosts(data.content || []);
      setTotalPages(data.totalPages || 0);
      setTotalElements(data.totalElements || 0);
    } catch (err) {
      setError(err?.response?.data?.message || 'Không tải được danh sách bài viết');
    } finally {
      setLoading(false);
    }
  };

  const loadCourses = async () => {
    const data = await getForumCourses();
    setCourses(data);
    const creatableCourses = data.filter((course) => course.canCreatePost);
    if (!createCourseId && creatableCourses.length > 0) setCreateCourseId(creatableCourses[0].id);
    if (createCourseId && !creatableCourses.some((course) => course.id === createCourseId)) {
      setCreateCourseId(creatableCourses[0]?.id || '');
      setPinned(false);
    }
  };

  const loadDetail = async (postId: string) => {
    setDetailLoading(true);
    setDetailError('');
    try {
      setDetail(await getForumPostDetail(postId));
    } catch (err) {
      setDetailError(err?.response?.data?.message || 'Không tải được chi tiết bài viết');
    } finally {
      setDetailLoading(false);
    }
  };

  useEffect(() => {
    loadCourses().catch(() => setError('Không tải được danh sách khóa học'));
  }, []);

  useEffect(() => {
    loadPosts();
  }, [courseId, filterTopic, page]);

  useEffect(() => {
    const timeout = window.setTimeout(() => {
      if (page !== 0) {
        setPage(0);
      } else {
        loadPosts();
      }
    }, 350);
    return () => window.clearTimeout(timeout);
  }, [q]);

  useEffect(() => {
    if (demo === 'detail' && posts[0]?.id && !selectedPostId) {
      setSelectedPostId(posts[0].id);
      loadDetail(posts[0].id);
    }
  }, [demo, posts, selectedPostId]);

  const openDetail = (postId: string) => {
    setSelectedPostId(postId);
    loadDetail(postId);
  };

  const closeDetail = async (refreshList?: boolean) => {
    setSelectedPostId(null);
    setDetail(null);
    if (refreshList) await loadPosts();
  };

  const handleCreatePost = async () => {
    if (!createCourseId || !title.trim() || !content.trim()) return;
    setSubmitting(true);
    try {
      const created = await createForumPost({ courseId: createCourseId, topic: createTopic || null, title: title.trim(), content: content.trim(), pinned });
      setCreateOpen(false);
      setCreateTopic('');
      setTitle('');
      setContent('');
      setPinned(false);
      await loadPosts();
      openDetail(created.id);
    } catch (err) {
      alert(err?.response?.data?.message || 'Không tạo được bài viết');
    } finally {
      setSubmitting(false);
    }
  };

  useEffect(() => {
    if (!selectedCreateCourse?.canPinPost && pinned) setPinned(false);
  }, [selectedCreateCourse, pinned]);

  if (selectedPostId) {
    return <ForumDetail detail={detail} setDetail={setDetail} loading={detailLoading} error={detailError} onBack={closeDetail} />;
  }

  return (
    <div className="page fade-in">
      <div className="page-head between"><div><h1 className="t-h1">Diễn đàn thảo luận</h1><p>Đặt câu hỏi, chia sẻ kiến thức cùng thành viên trong khóa học.</p></div><button className="btn btn-primary" onClick={() => setCreateOpen(true)} disabled={createCourses.length === 0}><Ic n="plus" size={17} />Tạo bài viết</button></div>
      <div className="toolbar"><Se placeholder="Tìm kiếm chủ đề..." value={q} onChange={setQ} /><Sl value={filterTopic} onChange={setFilterTopic} options={[{ v: '', label: 'Tất cả chủ đề' }, ...topicOptions.slice(1)]} style={{ width: 180, flex: 'none' }} /><Sl value={courseId} onChange={setCourseId} options={courseOptions} style={{ width: 260, flex: 'none' }} /></div>
      {error && <div className="card card-pad" style={{ marginBottom: 14, color: 'var(--danger)' }}>{error}</div>}
      <Sn pad={false}>
        <div style={{ padding: 8 }}>
          {loading && <div className="card-pad t-sm muted" style={{ display: 'flex', alignItems: 'center', gap: 10 }}><Ic n="message" size={18} />Đang tải bài viết...</div>}
          {!loading && posts.length === 0 && <div className="card-pad" style={{ textAlign: 'center', color: 'var(--text-2)', padding: '42px 24px' }}><Ic n="message" size={34} style={{ marginBottom: 10, color: 'var(--text-3)' }} /><div style={{ fontWeight: 800, color: 'var(--text)', fontSize: 16 }}>Chưa có bài viết nào</div><div className="t-sm muted" style={{ marginTop: 5 }}>Tạo chủ đề đầu tiên để bắt đầu thảo luận trong khóa học.</div></div>}
          {!loading && sortedPosts.map((post) => (
            <div key={post.id} className="row gap-16" style={{ padding: 16, borderRadius: 13, cursor: 'pointer', borderBottom: '1px solid var(--border)' }} onClick={() => openDetail(post.id)}>
              <Av name={post.author.fullName} size={46} />
              <div className="grow" style={{ minWidth: 0 }}>
                <div className="row gap-8 wrap" style={{ marginBottom: 5 }}>
                  {newestIds.has(post.id) && <span className="chip chip-new">New</span>}
                  {post.pinned && <span className="chip chip-warning"><Ic n="pin" size={12} />Ghim</span>}
                  {topicInfo(post.topic) && <span className={`chip ${topicInfo(post.topic).cls}`}>{topicInfo(post.topic).label}</span>}
                  <span className="chip chip-info">{post.courseTitle}</span>
                  {post.author.role === 'INSTRUCTOR' && <span className="chip chip-neutral">Giảng viên</span>}
                </div>
                <div style={{ fontWeight: 700, fontSize: 15.5 }} className="truncate">{post.title}</div>
                <div className="t-sm muted row gap-12" style={{ marginTop: 5 }}><span>{post.author.fullName}</span><span>•</span><span>{formatTime(post.createdAt)}</span></div>
              </div>
              <div className="row gap-20" style={{ flex: 'none' }}>
                <div style={{ textAlign: 'center' }}><div style={{ fontWeight: 800, fontSize: 17 }}>{post.replyCount}</div><div className="t-xs dim">trả lời</div></div>
                <div style={{ textAlign: 'center' }}><div style={{ fontWeight: 800, fontSize: 17, color: post.upvoted ? 'var(--primary)' : undefined }}>{post.upvoteCount}</div><div className="t-xs dim">thích</div></div>
              </div>
            </div>
          ))}
        </div>
      </Sn>
      <div className="between" style={{ marginTop: 14 }}>
        <div className="t-sm muted">{totalElements} chủ đề</div>
        <div className="row gap-8"><button className="btn btn-ghost btn-sm" disabled={page <= 0} onClick={() => setPage(page - 1)}>Trước</button><span className="t-sm muted">Trang {totalPages === 0 ? 0 : page + 1}/{totalPages}</span><button className="btn btn-ghost btn-sm" disabled={page + 1 >= totalPages} onClick={() => setPage(page + 1)}>Sau</button></div>
      </div>

      <Md open={createOpen} onClose={() => setCreateOpen(false)} max={620} maxHeight="calc(100vh - 48px)">
        <MH title="Tạo bài viết mới" sub="Chia sẻ câu hỏi hoặc thông tin với thành viên trong khóa học" icon="message" iconBg="#eaf1ff" iconColor="#2563eb" onClose={() => setCreateOpen(false)} />
        <div className="modal-body" style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          <div><label className="t-label" style={{ display: 'block', marginBottom: 7 }}>Khóa học</label><Sl value={createCourseId} onChange={setCreateCourseId} options={createCourseOptions} /></div>
          <div><label className="t-label" style={{ display: 'block', marginBottom: 7 }}>Chủ đề</label><Sl value={createTopic} onChange={setCreateTopic} options={topicOptions} /></div>
          <div><label className="t-label" style={{ display: 'block', marginBottom: 7 }}>Tiêu đề</label><input className="input" placeholder="VD: Hỏi về @Transactional trong Spring" value={title} onChange={(event) => setTitle(event.target.value)} /></div>
          <div><label className="t-label" style={{ display: 'block', marginBottom: 7 }}>Nội dung</label><textarea className="input" style={{ height: 118, padding: 12, resize: 'none' }} placeholder="Mô tả chi tiết nội dung bạn muốn trao đổi..." value={content} onChange={(event) => setContent(event.target.value)} /></div>
          {selectedCreateCourse?.canPinPost && <label className="row gap-8 t-sm"><input type="checkbox" checked={pinned} onChange={(event) => setPinned(event.target.checked)} />Ghim bài viết</label>}
        </div>
        <div className="modal-foot"><button className="btn btn-ghost" onClick={() => setCreateOpen(false)}>Hủy</button><button className="btn btn-primary" disabled={submitting || !createCourseId || !title.trim() || !content.trim()} onClick={handleCreatePost}><Ic n="send" size={16} />Đăng bài</button></div>
      </Md>
    </div>
  );
}

window.ForumPage = ForumPage;
window.StuForum = ForumPage;
