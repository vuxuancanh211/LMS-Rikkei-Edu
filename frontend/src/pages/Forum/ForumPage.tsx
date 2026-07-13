import React, { lazy, Suspense, useEffect, useState } from 'react';
import DOMPurify from 'dompurify';
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
  uploadForumAttachment,
  reportPost,
  reportReply,
  updateForumPost,
  updateForumReply,
  toAbsoluteApiUrl,
  toStableApiPath,
  type ForumAttachment,
  type ForumCourse,
  type ForumPost,
  type ForumPostDetail,
} from '../../services';
import { useAuthStore } from '../../store';

const ForumRichEditor = lazy(() => import('./ForumRichEditor'));

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
const TOP_LEVEL_REPLY_BATCH_SIZE = 10;
const CHILD_REPLY_BATCH_SIZE = 5;
const MAX_IMAGE_BYTES = 5 * 1024 * 1024;
const MAX_FILE_BYTES = 20 * 1024 * 1024;
const ALLOWED_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp', 'image/gif'];
const ALLOWED_FILE_TYPES = [
  'application/pdf',
  'application/msword',
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  'application/vnd.ms-powerpoint',
  'application/vnd.openxmlformats-officedocument.presentationml.presentation',
  'application/vnd.ms-excel',
  'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  'text/plain',
  'application/zip',
  'application/x-zip-compressed',
];

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

function upvotedButtonStyle(upvoted) {
  return upvoted ? { color: '#2563eb', background: '#eaf1ff', borderColor: '#bfdbfe' } : {};
}

function formatFileSize(bytes?: number) {
  if (!bytes) return '0 KB';
  if (bytes < 1024 * 1024) return `${Math.ceil(bytes / 1024)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

function validateUploadFile(file: File) {
  const isImage = ALLOWED_IMAGE_TYPES.includes(file.type);
  const isFile = ALLOWED_FILE_TYPES.includes(file.type);
  if (!isImage && !isFile) return 'Định dạng file không được hỗ trợ';
  if (isImage && file.size > MAX_IMAGE_BYTES) return 'Ảnh tối đa 5MB';
  if (isFile && file.size > MAX_FILE_BYTES) return 'File tối đa 20MB';
  return '';
}

function sanitizeForumHtml(html?: string, attachments: ForumAttachment[] = []) {
  if (!html) return '';
  return DOMPurify.sanitize(resolveForumContentUrls(html, attachments), {
    ADD_TAGS: ['pre', 'code', 'figure', 'figcaption'],
    ADD_ATTR: ['target', 'rel', 'class', 'src', 'width', 'height', 'alt'],
    ALLOWED_URI_REGEXP: /^(?:(?:(?:f|ht)tps?|mailto|tel|callto|cid|xmpp|data):|[^a-z]|[a-z+.-]+(?:[^a-z+.-:]|$))/i,
  });
}

function resolveForumContentUrls(content: string, attachments: ForumAttachment[] = []) {
  return content.replace(/(src|href)=(['"])([^'"]*\/api\/forum\/attachments\/([^/'"?]+)\/content(?:\?[^'"]*)?)\2/g, (_, attr, quote, url, attachmentId) => {
    let attachment = attachments.find((item) => item.id === attachmentId);
    if (!attachment) {
      const imageAttachments = attachments.filter((item) => item.attachmentType === 'IMAGE');
      if (imageAttachments.length === 1) {
        attachment = imageAttachments[0];
      } else {
        attachment = imageAttachments.find((item) => url.includes(item.id));
      }
    }
    return `${attr}=${quote}${toAbsoluteApiUrl(attachment?.url || url)}${quote}`;
  });
}

function normalizeForumContentForSave(content: string) {
  return content.replace(/(src|href)=(['"])([^'"]+)\2/g, (_, attr, quote, url) => `${attr}=${quote}${toStableApiPath(url)}${quote}`);
}

function isEditorContentBlank(content?: string) {
  if (!content) return true;
  const text = content
    .replace(/<[^>]*>/g, ' ')
    .replace(/&nbsp;/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
  return text.length === 0 && !/<img\s/i.test(content);
}

function attachmentIdsForSubmit(content: string, attachments: ForumAttachment[] = []) {
  return attachments
    .filter((attachment) => {
      if (attachment.attachmentType === 'FILE') return true;
      return content.includes(attachment.id) || content.includes(toStableApiPath(attachment.url)) || content.includes(toAbsoluteApiUrl(attachment.url));
    })
    .map((attachment) => attachment.id);
}

function ForumContent({ content, attachments = [], onImageClick }: { content?: string; attachments?: ForumAttachment[]; onImageClick?: (url: string) => void }) {
  const handleClick = (event: React.MouseEvent<HTMLDivElement>) => {
    if (!onImageClick || !(event.target instanceof HTMLImageElement)) return;
    event.preventDefault();
    onImageClick(event.target.currentSrc || event.target.src);
  };

  if (!content || !/<[a-z][\s\S]*>/i.test(content)) {
    return <div className="forum-content" style={{ whiteSpace: 'pre-line' }}>{content || ''}</div>;
  }

  return <div className="forum-content" onClick={handleClick} dangerouslySetInnerHTML={{ __html: sanitizeForumHtml(content, attachments) }} />;
}

function AttachmentList({ attachments = [] }: { attachments?: ForumAttachment[] }) {
  const Ic = window.Icon;
  const fileAttachments = attachments.filter((attachment) => attachment.attachmentType === 'FILE');
  if (!fileAttachments.length) return null;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 10, marginTop: 12 }}>
      {fileAttachments.map((attachment) => (
        <a key={attachment.id} href={toAbsoluteApiUrl(attachment.url)} target="_blank" rel="noreferrer" className="row gap-10" style={{ width: 'fit-content', maxWidth: '100%', padding: '10px 12px', border: '1px solid var(--border)', borderRadius: 10, background: 'var(--surface-2)', color: 'var(--text)', textDecoration: 'none' }}>
          <Ic n="file" size={18} />
          <span className="truncate" style={{ maxWidth: 340, fontWeight: 600 }}>{attachment.fileName}</span>
          <span className="t-xs dim">{formatFileSize(attachment.sizeBytes)}</span>
        </a>
      ))}
    </div>
  );
}

function ForumEditor({ value, onChange, attachments, onAttachmentsChange, placeholder, minHeight = 100, disabled = false }) {
  const Ic = window.Icon;
  const [uploading, setUploading] = useState(false);
  const [uploadError, setUploadError] = useState('');

  const handleFiles = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(event.target.files || []);
    event.target.value = '';
    if (files.length === 0) return;
    setUploadError('');

    if (files.some((file) => ALLOWED_IMAGE_TYPES.includes(file.type))) {
      setUploadError('Ảnh hãy upload trực tiếp trong editor bằng nút image hoặc kéo thả vào vùng soạn thảo. Nút này chỉ dùng để đính kèm file.');
      return;
    }

    for (const file of files) {
      const validationError = validateUploadFile(file);
      if (validationError) {
        setUploadError(`${file.name}: ${validationError}`);
        return;
      }
    }

    setUploading(true);
    try {
      const uploaded = [];
      for (const file of files) {
        uploaded.push(await uploadForumAttachment(file));
      }
      onAttachmentsChange([...(attachments || []), ...uploaded]);
    } catch (err) {
      setUploadError(err?.response?.data?.message || 'Không tải được file');
    } finally {
      setUploading(false);
    }
  };

  const removeAttachment = (id: string) => {
    onAttachmentsChange((attachments || []).filter((attachment) => attachment.id !== id));
  };

  return (
    <div>
      <div style={{ minHeight }}>
        <Suspense fallback={<div className="input" style={{ minHeight, padding: 12 }}>Đang tải editor...</div>}>
          <ForumRichEditor
          disabled={disabled || uploading}
          value={resolveForumContentUrls(value || '', attachments || [])}
          onChange={onChange}
          onUploadedAttachment={(attachment) => onAttachmentsChange((current = []) => [...current, attachment])}
          placeholder={placeholder}
        />
        </Suspense>
      </div>
      <div className="between" style={{ marginTop: 8 }}>
        <div className="row gap-8 wrap">
          <label className="btn btn-ghost btn-sm" style={{ cursor: uploading || disabled ? 'not-allowed' : 'pointer' }}>
            <Ic n="upload" size={15} />{uploading ? 'Đang tải...' : 'Đính kèm file'}
            <input type="file" multiple accept={ALLOWED_FILE_TYPES.join(',')} style={{ display: 'none' }} disabled={uploading || disabled} onChange={handleFiles} />
          </label>
          <span className="t-xs dim">Ảnh upload trong editor tối đa 5MB. File đính kèm: PDF/Office/TXT/ZIP tối đa 20MB.</span>
        </div>
      </div>
      {(attachments || []).length > 0 && (
        <div className="row gap-8 wrap" style={{ marginTop: 10 }}>
          {attachments.map((attachment) => (
            <span key={attachment.id} className="chip chip-neutral" style={{ maxWidth: 260 }}>
              <Ic n="file" size={13} />
              <span className="truncate" style={{ maxWidth: 170 }}>{attachment.fileName}</span>
              <button type="button" onClick={() => removeAttachment(attachment.id)} style={{ border: 0, background: 'transparent', cursor: 'pointer', color: 'var(--text-3)' }}>×</button>
            </span>
          ))}
        </div>
      )}
      {uploadError && <div className="t-xs" style={{ marginTop: 8, color: 'var(--danger)' }}>{uploadError}</div>}
    </div>
  );
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
  const [replyAttachments, setReplyAttachments] = useState<ForumAttachment[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [editPostOpen, setEditPostOpen] = useState(false);
  const [editTitle, setEditTitle] = useState('');
  const [editContent, setEditContent] = useState('');
  const [editAttachments, setEditAttachments] = useState<ForumAttachment[]>([]);
  const [editPinned, setEditPinned] = useState(false);
  const [editTopic, setEditTopic] = useState('');
  const [editingReplyId, setEditingReplyId] = useState<string | null>(null);
  const [editingReplyContent, setEditingReplyContent] = useState('');
  const [replyTarget, setReplyTarget] = useState(null);
  const [nestedReplyContent, setNestedReplyContent] = useState('');
  const [nestedReplyAttachments, setNestedReplyAttachments] = useState<ForumAttachment[]>([]);
  const [deleteTarget, setDeleteTarget] = useState<{ type: 'post' | 'reply'; id: string } | null>(null);
  const [reportTarget, setReportTarget] = useState<{ type: 'post' | 'reply'; id: string } | null>(null);
  const [reportReason, setReportReason] = useState('');
  const [reportDescription, setReportDescription] = useState('');
  const [previewImageUrl, setPreviewImageUrl] = useState('');
  const [visibleTopLevelReplyCount, setVisibleTopLevelReplyCount] = useState(TOP_LEVEL_REPLY_BATCH_SIZE);
  const [expandedReplyIds, setExpandedReplyIds] = useState(() => new Set());
  const [visibleChildReplyCounts, setVisibleChildReplyCounts] = useState({});

  useEffect(() => {
    if (!detail?.post) return;
    setEditTitle(detail.post.title);
    setEditContent(detail.post.content);
    setEditAttachments([]);
    setEditPinned(detail.post.pinned);
    setEditTopic(detail.post.topic || '');
    setVisibleTopLevelReplyCount(TOP_LEVEL_REPLY_BATCH_SIZE);
    setExpandedReplyIds(new Set());
    setVisibleChildReplyCounts({});
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
    if (isEditorContentBlank(replyContent)) return;
    setSubmitting(true);
    try {
      const createdReply = await createForumReply(post.id, { content: normalizeForumContentForSave(replyContent).trim(), attachmentIds: attachmentIdsForSubmit(replyContent, replyAttachments) });
      setReplyContent('');
      setReplyAttachments([]);
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
    if (!editTopic || !editTitle.trim() || isEditorContentBlank(editContent)) return;
    setSubmitting(true);
    try {
      const updatedPost = await updateForumPost(post.id, { topic: editTopic, title: editTitle.trim(), content: normalizeForumContentForSave(editContent).trim(), pinned: editPinned, attachmentIds: attachmentIdsForSubmit(editContent, editAttachments) });
      setEditPostOpen(false);
      setEditAttachments([]);
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
    setNestedReplyAttachments([]);
    setEditingReplyId(null);
  };

  const handleSubmitNestedReply = async () => {
    if (!replyTarget || isEditorContentBlank(nestedReplyContent)) return;
    setSubmitting(true);
    try {
      const createdReply = await createForumReply(post.id, { content: normalizeForumContentForSave(nestedReplyContent).trim(), parentReplyId: replyTarget.id, attachmentIds: attachmentIdsForSubmit(nestedReplyContent, nestedReplyAttachments) });
      setReplyTarget(null);
      setNestedReplyContent('');
      setNestedReplyAttachments([]);
      setExpandedReplyIds((current) => new Set([...current, replyTarget.id]));
      setVisibleChildReplyCounts((current) => ({ ...current, [replyTarget.id]: Math.max(current[replyTarget.id] || CHILD_REPLY_BATCH_SIZE, CHILD_REPLY_BATCH_SIZE) }));
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
    const expanded = expandedReplyIds.has(reply.id);
    const visibleChildReplyCount = visibleChildReplyCounts[reply.id] || CHILD_REPLY_BATCH_SIZE;
    const visibleChildReplies = expanded ? childReplies.slice(0, visibleChildReplyCount) : [];
    const remainingChildReplies = Math.max(0, childReplies.length - visibleChildReplies.length);

    return (
      <div key={reply.id} className={reply.depth === 1 ? 'card card-pad' : ''} style={reply.depth === 1 ? null : { paddingLeft: 18, borderLeft: '2px solid var(--border)', marginTop: 12 }}>
        <div className="row gap-12" style={{ alignItems: 'flex-start' }}>
          <Av name={reply.author.fullName} size={reply.depth === 1 ? 40 : 32} src={reply.author.avatarUrl} />
          <div className="grow" style={{ minWidth: 0 }}>
            <div className="between gap-8" style={{ marginBottom: 6 }}>
              <div className="row gap-8 wrap"><b style={{ fontSize: reply.depth === 1 ? 14 : 13.5 }}>{reply.author.fullName}</b>{reply.author.role === 'INSTRUCTOR' && <span className="chip chip-info" style={{ fontSize: 10.5, padding: '1px 8px' }}>Giảng viên</span>}<span className="t-xs dim">• {formatTime(reply.createdAt)}</span></div>
              <div className="row gap-6">
                <button className="btn-ghost" onClick={() => handleToggleReplyUpvote(reply.id)} style={{ display: 'inline-flex', alignItems: 'center', gap: 3, padding: '4px 8px', border: '1px solid transparent', borderRadius: 6, fontSize: 12, cursor: 'pointer', color: reply.upvoted ? '#2563eb' : 'var(--text-3)', background: 'transparent', ...upvotedButtonStyle(reply.upvoted) }}><Ic n="thumbs_up" size={12} />{reply.upvoteCount}</button>
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
            ) : <><ForumContent content={reply.content} attachments={reply.attachments || []} onImageClick={setPreviewImageUrl} /><AttachmentList attachments={reply.attachments || []} /></>}

            {replyTarget?.id === reply.id && (
              <div className="row gap-10" style={{ marginTop: 12, alignItems: 'flex-start' }}>
                <Av name={currentUser?.fullName || 'User'} size={30} src={currentUser?.avatarUrl} />
                <div className="grow">
                  <ForumEditor value={nestedReplyContent} onChange={setNestedReplyContent} attachments={nestedReplyAttachments} onAttachmentsChange={setNestedReplyAttachments} placeholder={`Trả lời ${reply.author.fullName}...`} minHeight={86} disabled={submitting} />
                  <div className="row gap-8" style={{ justifyContent: 'flex-end', marginTop: 8 }}><button className="btn btn-ghost btn-sm" onClick={() => setReplyTarget(null)}>Hủy</button><button className="btn btn-primary btn-sm" disabled={submitting || isEditorContentBlank(nestedReplyContent)} onClick={handleSubmitNestedReply}>Gửi</button></div>
                </div>
              </div>
            )}

            {childReplies.length > 0 && !expanded && <button className="btn btn-ghost btn-sm" style={{ marginTop: 12 }} onClick={() => setExpandedReplyIds((current) => new Set([...current, reply.id]))}><Ic n="message" size={14} />Xem {childReplies.length} câu trả lời</button>}
            {expanded && visibleChildReplies.length > 0 && <div style={{ marginTop: 12 }}>{visibleChildReplies.map(renderReply)}</div>}
            {expanded && remainingChildReplies > 0 && <button className="btn btn-ghost btn-sm" style={{ marginTop: 10 }} onClick={() => setVisibleChildReplyCounts((current) => ({ ...current, [reply.id]: visibleChildReplyCount + CHILD_REPLY_BATCH_SIZE }))}>Tải thêm câu trả lời ({remainingChildReplies})</button>}
          </div>
        </div>
      </div>
    );
  };

  const visibleReplies = (detail.replies || []).slice(0, visibleTopLevelReplyCount);
  const remainingTopLevelReplies = Math.max(0, (detail.replies || []).length - visibleReplies.length);

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
          <Av name={post.author.fullName} size={44} src={post.author.avatarUrl} />
          <div className="grow"><div style={{ fontWeight: 700, fontSize: 14.5 }}>{post.author.fullName}</div><div className="t-xs muted">{roleLabel(post.author.role)} • {formatTime(post.createdAt)}</div></div>
        </div>
        <ForumContent content={post.content} attachments={post.attachments || []} onImageClick={setPreviewImageUrl} />
        <AttachmentList attachments={post.attachments || []} />
        <div className="row gap-10" style={{ marginTop: 20, paddingTop: 16, borderTop: '1px solid var(--border)' }}>
          <button className="btn btn-ghost btn-sm" onClick={handleToggleUpvote} style={upvotedButtonStyle(post.upvoted)}><Ic n="thumbs_up" size={15} />{post.upvoteCount}</button>
          <button className="btn btn-ghost btn-sm"><Ic n="message" size={15} />{post.replyCount} trả lời</button>
          <button className="btn btn-ghost btn-sm" onClick={() => { setReportTarget({ type: 'post', id: post.id }); setReportReason(''); setReportDescription(''); }}><Ic n="flag" size={15} />Báo cáo</button>
        </div>
      </div>

      <div className="card card-pad" style={{ marginBottom: 18 }}>
        <div className="row gap-12" style={{ alignItems: 'flex-start' }}>
          <Av name={currentUser?.fullName || 'User'} size={40} src={currentUser?.avatarUrl} />
          <div className="grow">
            <ForumEditor value={replyContent} onChange={setReplyContent} attachments={replyAttachments} onAttachmentsChange={setReplyAttachments} placeholder="Viết câu trả lời của bạn..." minHeight={84} disabled={submitting} />
            <div className="between" style={{ marginTop: 10 }}>
              <button className="btn btn-primary btn-sm" disabled={submitting || isEditorContentBlank(replyContent)} onClick={handleSubmitReply}><Ic n="send" size={15} />Gửi trả lời</button>
            </div>
          </div>
        </div>
      </div>

      <div className="between" style={{ marginBottom: 14 }}>
        <h3 className="t-h3">{post.replyCount} câu trả lời</h3>
      </div>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
        {detail.replies.length === 0 && <div className="card card-pad" style={{ textAlign: 'center', color: 'var(--text-2)' }}><Ic n="message" size={28} style={{ marginBottom: 8, color: 'var(--text-3)' }} /><div style={{ fontWeight: 700, color: 'var(--text)' }}>Chưa có câu trả lời</div><div className="t-sm muted" style={{ marginTop: 4 }}>Hãy là người đầu tiên phản hồi chủ đề này.</div></div>}
        {visibleReplies.map(renderReply)}
        {remainingTopLevelReplies > 0 && <button className="btn btn-ghost" onClick={() => setVisibleTopLevelReplyCount((current) => current + TOP_LEVEL_REPLY_BATCH_SIZE)}>Tải thêm bình luận ({remainingTopLevelReplies})</button>}
      </div>

      <Md open={editPostOpen} onClose={() => setEditPostOpen(false)} max={620} maxHeight="calc(100vh - 48px)">
        <MH title="Sửa bài viết" sub="Cập nhật nội dung thảo luận trong khóa học" icon="message" iconBg="#eaf1ff" iconColor="#2563eb" onClose={() => setEditPostOpen(false)} />
        <div className="modal-body" style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          <div><label className="t-label" style={{ display: 'block', marginBottom: 7 }}>Chủ đề</label><Sl value={editTopic} onChange={setEditTopic} options={topicOptions} /></div>
          <div><label className="t-label" style={{ display: 'block', marginBottom: 7 }}>Tiêu đề</label><input className="input" value={editTitle} onChange={(event) => setEditTitle(event.target.value)} /></div>
          <div><label className="t-label" style={{ display: 'block', marginBottom: 7 }}>Nội dung</label><ForumEditor value={editContent} onChange={setEditContent} attachments={editAttachments} onAttachmentsChange={setEditAttachments} placeholder="Cập nhật nội dung bài viết..." minHeight={118} disabled={submitting} /></div>
          {(role === 'instructor' || role === 'admin') && <label className="row gap-8 t-sm"><input type="checkbox" checked={editPinned} onChange={(event) => setEditPinned(event.target.checked)} />Ghim bài viết</label>}
        </div>
        <div className="modal-foot"><button className="btn btn-ghost" onClick={() => setEditPostOpen(false)}>Hủy</button><button className="btn btn-primary" disabled={submitting || !editTopic || !editTitle.trim() || isEditorContentBlank(editContent)} onClick={handleUpdatePost}>Lưu thay đổi</button></div>
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

      <Md open={!!previewImageUrl} onClose={() => setPreviewImageUrl('')} max="fit-content" maxHeight="92vh">
        <div className="forum-image-preview-modal">
          <button type="button" className="forum-image-lightbox-close" onClick={() => setPreviewImageUrl('')} aria-label="Đóng ảnh">×</button>
          <img src={previewImageUrl} alt="Ảnh đính kèm" />
        </div>
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
  const [createAttachments, setCreateAttachments] = useState<ForumAttachment[]>([]);
  const [pinned, setPinned] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const courseOptions = [{ v: '', label: 'Tất cả khóa học' }, ...courses.map((course) => ({ v: course.id, label: course.title }))];
  const createCourses = courses.filter((course) => course.canCreatePost);
  const createCourseOptions = createCourses.map((course) => ({ v: course.id, label: course.title }));
  const selectedCreateCourse = createCourses.find((course) => course.id === createCourseId);
  const newestIds = new Set([...posts].sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()).slice(0, 3).map(p => p.id));
  const sortedPosts = posts;

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
    const pid = new URLSearchParams(window.location.search).get('postId');
    if (pid && pid !== selectedPostId) {
      openDetail(pid);
    }
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
    try {
      const url = new URL(window.location.href);
      if (url.searchParams.has('postId')) {
        url.searchParams.delete('postId');
        window.history.pushState({}, '', url);
      }
    } catch { /* ignore */ }
    if (refreshList) await loadPosts();
  };

  const handleCreatePost = async () => {
    if (!createCourseId || !createTopic || !title.trim() || isEditorContentBlank(content)) return;
    setSubmitting(true);
    try {
      const created = await createForumPost({ courseId: createCourseId, topic: createTopic, title: title.trim(), content: normalizeForumContentForSave(content).trim(), pinned, attachmentIds: attachmentIdsForSubmit(content, createAttachments) });
      setCreateOpen(false);
      setCreateTopic('');
      setTitle('');
      setContent('');
      setCreateAttachments([]);
      setPinned(false);
      await loadPosts();
      openDetail(created.id);
    } catch (err) {
      setError(err?.response?.data?.message || 'Không tạo được bài viết');
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
              <Av name={post.author.fullName} size={46} src={post.author.avatarUrl} />
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
                <div style={{ textAlign: 'center' }}><div style={{ fontWeight: 800, fontSize: 17, color: post.upvoted ? '#2563eb' : undefined }}>{post.upvoteCount}</div><div className="t-xs dim">thích</div></div>
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
          <div><label className="t-label" style={{ display: 'block', marginBottom: 7 }}>Nội dung</label><ForumEditor value={content} onChange={setContent} attachments={createAttachments} onAttachmentsChange={setCreateAttachments} placeholder="Mô tả chi tiết nội dung bạn muốn trao đổi..." minHeight={118} disabled={submitting} /></div>
          {selectedCreateCourse?.canPinPost && <label className="row gap-8 t-sm"><input type="checkbox" checked={pinned} onChange={(event) => setPinned(event.target.checked)} />Ghim bài viết</label>}
        </div>
        <div className="modal-foot"><button className="btn btn-ghost" onClick={() => setCreateOpen(false)}>Hủy</button><button className="btn btn-primary" disabled={submitting || !createCourseId || !createTopic || !title.trim() || isEditorContentBlank(content)} onClick={handleCreatePost}><Ic n="send" size={16} />Đăng bài</button></div>
      </Md>
    </div>
  );
}

window.ForumPage = ForumPage;
window.StuForum = ForumPage;
