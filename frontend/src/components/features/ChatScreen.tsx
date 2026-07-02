import { useCallback, useEffect, useRef, useState } from 'react';
import { useAuthStore } from '../../store';
import {
  addReaction,
  deleteMessage,
  getRoomDetail,
  getRooms,
  getMessages,
  markAsRead,
  presignUpload,
  removeReaction,
  sendMessage,
  type ChatMessageResponse,
  type ChatRoomResponse,
} from '../../services/chat/chat-service';
import {
  connectStomp,
  disconnectStomp,
  isConnected,
  sendMessageViaStomp,
  subscribeRoom,
} from '../../services/chat/stomp-client';

type MsgGroup = {
  senderId: string;
  senderName: string;
  messages: ChatMessageResponse[];
};

function nameColor(name: string): string {
  let hash = 0;
  for (let i = 0; i < name.length; i++) {
    hash = name.charCodeAt(i) + ((hash << 5) - hash);
  }
  const h = Math.abs(hash) % 360;
  return `hsl(${h}, 55%, 45%)`;
}

function groupMessages(msgs: ChatMessageResponse[]): MsgGroup[] {
  const groups: MsgGroup[] = [];
  for (const m of msgs) {
    const last = groups[groups.length - 1];
    if (last && last.senderId === m.senderId) {
      last.messages.push(m);
    } else {
      groups.push({ senderId: m.senderId, senderName: m.senderName, messages: [m] });
    }
  }
  return groups;
}

function ChatScreen({ nav, persona, demo }) {
  const userId = useAuthStore((s) => s.user?.id);

  const [rooms, setRooms] = useState<ChatRoomResponse[]>([]);
  const [activeRoomId, setActiveRoomId] = useState<string | null>(null);
  const [messages, setMessages] = useState<ChatMessageResponse[]>([]);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(true);
  const [sending, setSending] = useState(false);
  const [sendingFile, setSendingFile] = useState(false);
  const [error, setError] = useState('');
  const [searchTerm, setSearchTerm] = useState('');
  const [page, setPage] = useState(0);
  const [hasMore, setHasMore] = useState(false);
  const loadingMoreRef = useRef(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [hoveredId, setHoveredId] = useState<string | null>(null);
  const [menuMsgId, setMenuMsgId] = useState<string | null>(null);
  const [pickerMsgId, setPickerMsgId] = useState<string | null>(null);
  const [roomDetail, setRoomDetail] = useState<ChatRoomResponse | null>(null);
  const [hoverReaction, setHoverReaction] = useState<string | null>(null);
  const [replyTarget, setReplyTarget] = useState<ChatMessageResponse | null>(null);

  const EMOJIS = ['👍', '❤️', '😂', '😮', '😢'];
  const threadRef = useRef<HTMLDivElement>(null);
  const bottomRef = useRef<HTMLDivElement>(null);
  const pickerTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const roomIdsRef = useRef<string[]>([]);
  const activeRoomIdRef = useRef<string | null>(null);
  const pageRef = useRef(0);
  const hasMoreRef = useRef(false);
  const roomGenRef = useRef(0);
  const myReactionsRef = useRef<Record<string, string[]>>({});

  useEffect(() => {
    setLoading(true);
    setError('');

    const subscribeAllRooms = (ids: string[]) => {
      ids.forEach((id) =>
        subscribeRoom(id, (msg) => {
          try {
            const body = JSON.parse(msg.body);
            if (body.event === 'CHAT_MESSAGE' && body.message) {
              if (id === activeRoomIdRef.current) {
                setMessages((prev) => [...prev, body.message]);
                handleMarkAsRead(id, body.message.id);
              } else {
                setRooms((prev) =>
                  prev.map((r) =>
                    r.id === id
                      ? {
                          ...r,
                          lastMessage: body.message,
                          lastMessageAt: body.message.createdAt,
                          unreadCount: (r.unreadCount || 0) + 1,
                        }
                      : r,
                  ),
                );
              }
            } else if (body.event === 'MESSAGE_DELETED' && body.message) {
              if (id === activeRoomIdRef.current) {
                setMessages((prev) =>
                  prev.map((m) => (m.id === body.message.id ? { ...m, deleted: true } : m)),
                );
              }
            } else if (
              (body.event === 'REACTION_ADDED' || body.event === 'REACTION_REMOVED') &&
              body.messageId &&
              body.reactions
            ) {
              if (id === activeRoomIdRef.current) {
                setMessages((prev) =>
                  prev.map((m) =>
                    m.id === body.messageId ? { ...m, reactions: body.reactions } : m,
                  ),
                );
              }
            }
          } catch (e) {
            console.warn('Invalid STOMP message body', msg.body);
          }
        }),
      );
    };

    getRooms()
      .then((data) => {
        setRooms(data);
        roomIdsRef.current = data.map((r) => r.id);
        if (data.length > 0 && !activeRoomId) {
          setActiveRoomId(data[0].id);
        }
        if (isConnected()) {
          subscribeAllRooms(roomIdsRef.current);
        }
      })
      .catch((err) => setError(err?.response?.data?.message || 'Không tải được danh sách phòng'))
      .finally(() => setLoading(false));

    if (!isConnected()) {
      connectStomp(
        () => {
          subscribeAllRooms(roomIdsRef.current);
        },
        (msg) => console.warn('STOMP error:', msg),
      );
    }

    return () => {
      disconnectStomp();
    };
  }, []);

  useEffect(() => {
    activeRoomIdRef.current = activeRoomId;
  }, [activeRoomId]);

  useEffect(() => { pageRef.current = page; }, [page]);
  useEffect(() => { hasMoreRef.current = hasMore; }, [hasMore]);

  useEffect(() => {
    if (!activeRoomId) return;
    roomGenRef.current += 1;

    setMessages([]);
    setPage(0);
    setHasMore(false);
    getMessages(activeRoomId, 0)
      .then((res) => {
        const msgs = (res.content || []).reverse();
        setMessages(msgs);
        setHasMore(!res.last);
        if (msgs.length > 0) {
          handleMarkAsRead(activeRoomId, msgs[msgs.length - 1].id);
        }
        setTimeout(() => bottomRef.current?.scrollIntoView({ behavior: 'instant' }), 100);
      })
      .catch((err) => setError(err?.response?.data?.message || 'Không tải được tin nhắn'));
  }, [activeRoomId]);

  useEffect(() => {
    if (messages.length > 0) {
      const el = threadRef.current;
      if (!el) return;
      const isNearBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 200;
      if (isNearBottom) {
        bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
      }
    }
  }, [messages]);

  useEffect(() => {
    const el = threadRef.current;
    if (!el || !activeRoomId) return;

    const onScroll = () => {
      if (loadingMoreRef.current || !hasMoreRef.current) return;
      if (el.scrollTop > 80) return;

      loadingMoreRef.current = true;
      setLoadingMore(true);
      const prevPage = pageRef.current;
      const nextPage = prevPage + 1;
      const prevScrollHeight = el.scrollHeight;
      const gen = roomGenRef.current;

      getMessages(activeRoomId, nextPage)
        .then((res) => {
          if (gen !== roomGenRef.current) return;
          const older = (res.content || []).reverse();
          if (older.length === 0) {
            setHasMore(false);
            loadingMoreRef.current = false;
            setLoadingMore(false);
            return;
          }
          setMessages((prev) => [...older, ...prev]);
          setPage(nextPage);
          setHasMore(!res.last);

          requestAnimationFrame(() => {
            if (threadRef.current) {
              threadRef.current.scrollTop = threadRef.current.scrollHeight - prevScrollHeight;
            }
            loadingMoreRef.current = false;
            setLoadingMore(false);
          });
        })
        .catch(() => {
          loadingMoreRef.current = false;
          setLoadingMore(false);
        });
    };

    el.addEventListener('scroll', onScroll, { passive: true });
    return () => el.removeEventListener('scroll', onScroll);
  }, [activeRoomId]);

  useEffect(() => {
    if (!error) return;
    const t = setTimeout(() => setError(''), 3000);
    return () => clearTimeout(t);
  }, [error]);

  const handleMarkAsRead = useCallback((roomId: string, messageId: string) => {
    markAsRead(roomId, messageId);
    setRooms((prev) => prev.map((r) => (r.id === roomId ? { ...r, unreadCount: 0 } : r)));
  }, []);

  useEffect(() => {
    const close = () => {
      setMenuMsgId(null);
      setHoveredId(null);
      setPickerMsgId(null);
      setHoverReaction(null);
      setRoomDetail(null);
    };
    window.addEventListener('click', close);
    return () => window.removeEventListener('click', close);
  }, []);

  useEffect(() => {
    if (replyTarget) {
      const el = threadRef.current;
      if (el) {
        el.scrollTo({ top: el.scrollHeight, behavior: 'smooth' });
      }
    }
  }, [replyTarget]);

  const handleSend = useCallback(async () => {
    const text = input.trim();
    if (!text || !activeRoomId || sending) return;
    setSending(true);

    const payload: any = { content: text, messageType: 'TEXT' as const };
    if (replyTarget) {
      payload.replyToId = replyTarget.id;
    }

    const stompSent = sendMessageViaStomp(activeRoomId, payload);
    if (!stompSent) {
      try {
        const newMsg = await sendMessage(activeRoomId, payload);
        setRooms((prev) =>
          prev.map((r) =>
            r.id === activeRoomId
              ? { ...r, lastMessage: newMsg, lastMessageAt: newMsg.createdAt, unreadCount: 0 }
              : r,
          ),
        );
      } catch (err) {
        setError(err?.response?.data?.message || 'Gửi tin nhắn thất bại');
        setSending(false);
        return;
      }
    }

    setInput('');
    setReplyTarget(null);
    setSending(false);
    setTimeout(() => inputRef.current?.focus(), 0);
  }, [input, activeRoomId, sending, replyTarget]);

  const handleReaction = async (messageId: string, emoji: string) => {
    setPickerMsgId(null);
    try {
      const reactions = await addReaction(messageId, emoji);
      setMessages((prev) => prev.map((m) => (m.id === messageId ? { ...m, reactions } : m)));
      const my = myReactionsRef.current;
      const userEmojis = my[messageId] || [];
      if (!userEmojis.includes(emoji)) {
        my[messageId] = [...userEmojis, emoji];
      }
    } catch (err) { /* silent */ }
  };

  const handleRemoveReaction = async (messageId: string, emoji: string) => {
    try {
      const reactions = await removeReaction(messageId, emoji);
      setMessages((prev) => prev.map((m) => (m.id === messageId ? { ...m, reactions } : m)));
      const my = myReactionsRef.current;
      if (my[messageId]) {
        my[messageId] = my[messageId].filter((e) => e !== emoji);
      }
    } catch (err) {
      // silent
    }
  };

  const handleDelete = async (messageId: string, senderId: string) => {
    setMenuMsgId(null);
    setHoveredId(null);
    if (senderId === userId) {
      setMessages((prev) => prev.map((m) => (m.id === messageId ? { ...m, deleted: true } : m)));
      try {
        await deleteMessage(messageId);
      } catch (err) {
        setError(err?.response?.data?.message || 'Xoá tin nhắn thất bại');
        setMessages((prev) => prev.map((m) => (m.id === messageId ? { ...m, deleted: false } : m)));
      }
    } else {
      setMessages((prev) => prev.filter((m) => m.id !== messageId));
    }
  };

  const handleFileSelect = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file || !activeRoomId) return;
    setSendingFile(true);
    setError('');
    try {
      const { uploadUrl, viewUrl } = await presignUpload(activeRoomId, file.name, file.type);
      await fetch(uploadUrl, { method: 'PUT', body: file, headers: { 'Content-Type': file.type } });
      const payload: any = { messageType: 'FILE', attachmentUrl: viewUrl, attachmentName: file.name, attachmentSizeBytes: file.size };
      const stompSent = sendMessageViaStomp(activeRoomId, payload);
      if (!stompSent) {
        const newMsg = await sendMessage(activeRoomId, payload);
        setRooms((prev) =>
          prev.map((r) =>
            r.id === activeRoomId
              ? { ...r, lastMessage: newMsg, lastMessageAt: newMsg.createdAt, unreadCount: 0 }
              : r,
          ),
        );
      }
    } catch (err: any) {
      setError(err?.response?.data?.message || err?.response?.data?.error || err?.message || 'Gửi file thất bại');
    }
    setSendingFile(false);
    if (fileInputRef.current) fileInputRef.current.value = '';
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  const activeRoom = rooms.find((r) => r.id === activeRoomId);
  const filteredRooms = searchTerm
    ? rooms.filter((r) => r.name?.toLowerCase().includes(searchTerm.toLowerCase()))
    : rooms;

  return (
    <div className="page fade-in" style={{ paddingBottom: 0 }}>
      <div className="page-head">
        <h1 className="t-h1">Chat nhóm</h1>
      </div>

      <div
        className="card chat-shell"
        style={{
          overflow: 'hidden',
          display: 'grid',
          gridTemplateColumns: '320px 1fr',
          height: 620,
        }}
      >
        <div
          className="chat-list"
          style={{
            borderRight: '1px solid var(--border)',
            display: 'flex',
            flexDirection: 'column',
            minHeight: 0,
          }}
        >
          <div style={{ padding: 14, borderBottom: '1px solid var(--border)' }}>
            <div className="field-icon">
              <Ic n="search" />
              <input
                className="input"
                placeholder="Tìm cuộc trò chuyện..."
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
              />
            </div>
          </div>
          <div style={{ overflowY: 'auto', flex: 1 }}>
            {loading && (
              <div className="t-sm muted" style={{ padding: 24, textAlign: 'center' }}>
                Đang tải...
              </div>
            )}
            {!loading && rooms.length === 0 && (
              <div className="t-sm muted" style={{ padding: 24, textAlign: 'center' }}>
                Chưa có phòng chat nào
              </div>
            )}
            {!loading && rooms.length > 0 && filteredRooms.length === 0 && (
              <div className="t-sm muted" style={{ padding: 24, textAlign: 'center' }}>
                Không tìm thấy phòng phù hợp
              </div>
            )}
            {!loading &&
              filteredRooms.map((r) => (
                <div
                  key={r.id}
                  className="row gap-12"
                  style={{
                    padding: '13px 16px',
                    cursor: 'pointer',
                    background: activeRoomId === r.id ? 'var(--accent-soft)' : 'transparent',
                    borderLeft:
                      '3px solid ' + (activeRoomId === r.id ? 'var(--accent)' : 'transparent'),
                  }}
                  onClick={() => setActiveRoomId(r.id)}
                >
                  <div style={{ position: 'relative', flex: 'none' }}>
                    <div
                      className="stat-ic"
                      style={{
                        width: 44,
                        height: 44,
                        borderRadius: 12,
                        background: '#f3edff',
                        color: '#7c3aed',
                      }}
                    >
                      <Ic n="layers" size={21} />
                    </div>
                  </div>
                  <div className="grow" style={{ minWidth: 0 }}>
                    <div className="between">
                      <b style={{ fontSize: 14 }} className="truncate">
                        {r.name}
                      </b>
                      <span className="t-xs dim" style={{ flex: 'none' }}>
                        {r.lastMessage ? formatTime(r.lastMessage.createdAt) : ''}
                      </span>
                    </div>
                    <div className="between" style={{ marginTop: 3 }}>
                      <span className="t-sm muted truncate">
                        {r.lastMessage?.content || (r.lastMessage ? '[File]' : '')}
                      </span>
                      {r.unreadCount > 0 && (
                        <span
                          style={{
                            flex: 'none',
                            background: 'var(--accent)',
                            color: '#fff',
                            fontSize: 11,
                            fontWeight: 700,
                            minWidth: 20,
                            height: 20,
                            borderRadius: 999,
                            display: 'grid',
                            placeItems: 'center',
                            padding: '0 6px',
                          }}
                        >
                          {r.unreadCount}
                        </span>
                      )}
                    </div>
                  </div>
                </div>
              ))}
          </div>
        </div>

        <div
          className="chat-thread"
          style={{
            display: 'flex',
            flexDirection: 'column',
            minHeight: 0,
            minWidth: 0,
            background: 'var(--surface-2)',
          }}
        >
          <div
            className="between"
            style={{
              padding: '14px 20px',
              borderBottom: '1px solid var(--border)',
              background: '#fff',
            }}
          >
            <div className="row gap-12">
              <div
                className="stat-ic"
                style={{
                  width: 42,
                  height: 42,
                  borderRadius: 11,
                  background: '#f3edff',
                  color: '#7c3aed',
                }}
              >
                <Ic n="layers" size={20} />
              </div>
              <div>
                <div style={{ fontWeight: 700, fontSize: 15 }}>
                  {activeRoom?.name || 'Chọn phòng chat'}
                </div>
              </div>
            </div>
            <div className="row gap-8" style={{ position: 'relative' }}>
              <button
                className="icon-btn"
                style={{ width: 38, height: 38 }}
                onClick={(e) => {
                  e.stopPropagation();
                  if (roomDetail) { setRoomDetail(null); return; }
                  if (activeRoomId) {
                    getRoomDetail(activeRoomId).then((detail) => {
                      setRooms((prev) =>
                        prev.map((r) => (r.id === detail.id ? { ...r, members: detail.members } : r)),
                      );
                      setRoomDetail(detail);
                    });
                  }
                }}
              >
                <Ic n="dots" size={18} />
              </button>
              {roomDetail && (
                <div
                  onClick={(e) => e.stopPropagation()}
                  style={{
                    position: 'absolute',
                    top: '100%',
                    right: 0,
                    marginTop: 6,
                    background: '#fff',
                    border: '1px solid var(--border)',
                    borderRadius: 10,
                    boxShadow: 'var(--sh-md)',
                    padding: 12,
                    zIndex: 30,
                    minWidth: 220,
                  }}
                >
                  <div className="between" style={{ marginBottom: 8 }}>
                    <b style={{ fontSize: 14 }}>{roomDetail.name}</b>
                    <button
                      style={{ border: 'none', background: 'transparent', cursor: 'pointer', color: 'var(--text-3)', fontSize: 16, padding: 0 }}
                      onClick={() => setRoomDetail(null)}
                    >
                      ✕
                    </button>
                  </div>
                  <div className="t-xs" style={{ color: 'var(--text-3)', marginBottom: 6 }}>
                    {roomDetail.members?.length || 0} thành viên
                  </div>
                  <div style={{ maxHeight: 200, overflowY: 'auto' }}>
                    {roomDetail.members?.map((m) => (
                      <div key={m.userId} className="row gap-8" style={{ padding: '4px 0' }}>
                        <Av name={m.fullName} size={28} />
                        <span style={{ fontSize: 13 }}>{m.fullName}</span>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>
          </div>

          <div
            ref={threadRef}
            style={{ flex: 1, overflowY: 'auto', padding: '20px 20px 0', minWidth: 0 }}
          >
            {loadingMore && (
              <div className="t-sm muted" style={{ textAlign: 'center', padding: '12px 0' }}>
                Đang tải tin nhắn cũ...
              </div>
            )}
            {messages.length === 0 && (
              <div className="t-sm muted" style={{ textAlign: 'center', paddingTop: 40 }}>
                Chưa có tin nhắn nào. Hãy bắt đầu cuộc trò chuyện!
              </div>
            )}
            {(() => {
              const groups = groupMessages(messages);
              let prevTime: number | null = null;
              return groups.map((group) => {
                const firstMsg = group.messages[0];
                const firstTime = new Date(firstMsg.createdAt).getTime();
                const showDivider = prevTime !== null && firstTime - prevTime > 600000;
                prevTime = new Date(group.messages[group.messages.length - 1].createdAt).getTime();
                return (
                  <div key={group.senderId + '-' + firstMsg.id}>
                    {showDivider && (
                      <div
                        style={{ textAlign: 'center', margin: '18px 0 8px', userSelect: 'none' }}
                      >
                        <span
                          style={{
                            display: 'inline-block',
                            padding: '4px 14px',
                            borderRadius: 999,
                            fontSize: 12,
                            fontWeight: 600,
                            color: 'var(--text-3)',
                            background: 'var(--surface-2)',
                          }}
                        >
                          {formatTimeDivider(firstMsg.createdAt)}
                        </span>
                      </div>
                    )}
                    {group.messages.map((m, i) => {
                      const isFirst = i === 0;
                      const isLast = i === group.messages.length - 1;
                      const isMe = m.senderId === userId;
                      const isDeleted = m.deleted;
                      const showInnerDivider = i > 0 &&
                        (new Date(m.createdAt).getTime() - new Date(group.messages[i - 1].createdAt).getTime()) > 600000;
                      const showAvatar = isFirst || showInnerDivider;

                      return (
                        <React.Fragment key={m.id}>
                          {showInnerDivider && (
                            <div
                              style={{ textAlign: 'center', margin: '18px 0 8px', userSelect: 'none' }}
                            >
                              <span
                                style={{
                                  display: 'inline-block',
                                  padding: '4px 14px',
                                  borderRadius: 999,
                                  fontSize: 12,
                                  fontWeight: 600,
                                  color: 'var(--text-3)',
                                  background: 'var(--surface-2)',
                                }}
                              >
                                {formatTimeDivider(m.createdAt)}
                              </span>
                            </div>
                          )}
                          <div
                            className="msg-row"
                            style={{
                              display: 'flex',
                              gap: 8,
                              flexDirection: isMe ? 'row-reverse' : 'row',
                              width: '100%',
                              marginTop: showAvatar ? (showDivider ? 4 : 14) : 2,
                              marginBottom: isLast ? 4 : 0,
                            }}
                            onMouseEnter={() => setHoveredId(m.id)}
                            onMouseLeave={() => {
                              if (menuMsgId !== m.id + '-menu') setHoveredId(null);
                            }}
                          >
                            {!isMe && showAvatar && <Av name={m.senderName} size={32} />}
                            {!isMe && !showAvatar && <div style={{ width: 32, flex: 'none' }} />}
                            <div style={{ maxWidth: '72%', minWidth: 0, position: 'relative' }}>
                              <div
                                style={{
                                  padding: '8px 14px 14px',
                                  borderRadius: 12,
                                  fontSize: 14,
                                  lineHeight: 1.5,
                                  background: isMe ? '#e8f4fd' : '#fff',
                                  color: 'var(--text)',
                                  border: isMe ? '1px solid #cce6f5' : '1px solid var(--border)',
                                  borderBottomRightRadius: isMe ? (isLast ? 3 : 12) : 12,
                                  borderBottomLeftRadius: isMe ? 12 : isLast ? 3 : 12,
                                  overflowWrap: 'break-word',
                                  wordBreak: 'break-word',
                                }}
                              >
                                {!isMe && showAvatar && !isDeleted && (
                                  <div
                                    style={{
                                      fontSize: 12,
                                      fontWeight: 600,
                                      color: nameColor(m.senderName),
                                      marginBottom: 2,
                                      lineHeight: 1.3,
                                      userSelect: 'none',
                                    }}
                                  >
                                    {m.senderName}
                                  </div>
                                )}
                              {m.replyToId &&
                                (m.replyToContent ||
                                  m.replyToAttachmentName ||
                                  m.replyToSenderName) && (
                                  <div
                                    style={{
                                      padding: '8px 10px',
                                      marginBottom: 6,
                                      borderRadius: 8,
                                      background: isMe ? '#d4e8f4' : 'var(--surface-2)',
                                      borderLeft: '3px solid var(--accent)',
                                      fontSize: 12,
                                      lineHeight: 1.4,
                                    }}
                                  >
                                    <div
                                      style={{
                                        fontWeight: 600,
                                        color: nameColor(m.replyToSenderName || ''),
                                        marginBottom: 2,
                                      }}
                                    >
                                      {m.replyToSenderName || 'Người dùng'}
                                    </div>
                                    <span style={{ color: 'var(--text-3)' }}>
                                      {m.replyToAttachmentName || m.replyToContent || 'Tin nhắn'}
                                    </span>
                                  </div>
                                )}
                              {isDeleted ? (
                                <span
                                  className="t-xs"
                                  style={{ fontStyle: 'italic', color: 'var(--text-3)' }}
                                >
                                  Tin nhắn đã được xoá
                                </span>
                              ) : m.messageType === 'FILE' && m.attachmentName ? (
                                <a
                                  href={m.attachmentUrl || '#'}
                                  target="_blank"
                                  rel="noopener noreferrer"
                                  style={{ color: '#2563eb', textDecoration: 'underline', display: 'inline-flex', alignItems: 'center', gap: 4 }}
                                  onClick={(e) => e.stopPropagation()}
                                >
                                  <Ic n="file" size={16} /> {m.attachmentName}
                                </a>
                              ) : (
                                linkifyText(m.content)
                              )}
                              {!isDeleted && isLast && (
                                <div
                                  style={{
                                    textAlign: isMe ? 'right' : 'left',
                                    marginTop: 2,
                                    fontSize: 11,
                                    lineHeight: 1,
                                    color: 'var(--text-3)',
                                    userSelect: 'none',
                                  }}
                                >
                                  {formatTime(m.createdAt)}
                                </div>
                              )}
                            </div>
                            {!isDeleted && (
                              <div
                                style={{
                                  position: 'absolute',
                                  bottom: -8,
                                  right: 8,
                                  display: 'flex',
                                  gap: 3,
                                  alignItems: 'center',
                                  zIndex: 5,
                                }}
                              >
                                {m.reactions && Object.keys(m.reactions).length > 0 && (
                                  <div
                                    style={{
                                      display: 'flex',
                                      gap: 0,
                                      alignItems: 'center',
                                      border: '1px solid var(--border)',
                                      borderRadius: 999,
                                      background: '#fff',
                                      padding: '1px 2px',
                                      boxShadow: 'var(--sh-sm)',
                                    }}
                                  >
                                    {Object.entries(m.reactions)
                                      .slice(0, 3)
                                      .map(([emoji, count]) => (
                                        <button
                                          key={emoji}
                                          onClick={(e) => {
                                            e.stopPropagation();
                                            handleReaction(m.id, emoji);
                                          }}
                                          onMouseEnter={() => setHoverReaction(m.id + emoji)}
                                          onMouseLeave={() => setHoverReaction(null)}
                                          style={{
                                            border: 'none',
                                            background: 'transparent',
                                            padding: '1px 2px',
                                            cursor: 'pointer',
                                            fontSize: 12,
                                            lineHeight: '18px',
                                            display: 'flex',
                                            alignItems: 'center',
                                            gap: 0,
                                            position: 'relative',
                                            borderRadius: 999,
                                          }}
                                        >
                                          {emoji}
                                        </button>
                                      ))}
                                    <span
                                      style={{
                                        fontSize: 11,
                                        color: 'var(--text-3)',
                                        lineHeight: '18px',
                                        padding: '0 2px',
                                      }}
                                    >
                                      {Object.values(m.reactions).reduce((s, c) => s + c, 0)}
                                    </span>
                                  </div>
                                )}
                                <div
                                  style={{
                                    position: 'relative',
                                    visibility:
                                      hoveredId === m.id || menuMsgId === m.id + '-menu'
                                        ? 'visible'
                                        : 'hidden',
                                  }}
                                  onMouseOver={() => {
                                    if (pickerTimerRef.current)
                                      clearTimeout(pickerTimerRef.current);
                                    setPickerMsgId(m.id);
                                  }}
                                  onMouseOut={() => {
                                    pickerTimerRef.current = setTimeout(
                                      () => setPickerMsgId(null),
                                      150,
                                    );
                                  }}
                                >
                                  <button
                                    style={{
                                      border: '1px solid var(--border)',
                                      borderRadius: 999,
                                      background: '#fff',
                                      width: 22,
                                      height: 22,
                                      cursor: 'pointer',
                                      display: 'grid',
                                      placeItems: 'center',
                                      fontSize: 12,
                                      lineHeight: 1,
                                      color: 'var(--text-2)',
                                      padding: 0,
                                      boxShadow: 'var(--sh-sm)',
                                    }}
                                  >
                                    👍
                                  </button>
                                  {pickerMsgId === m.id && (
                                    <div
                                      style={{
                                        position: 'absolute',
                                        bottom: '100%',
                                        marginBottom: 6,
                                        background: '#fff',
                                        border: '1px solid var(--border)',
                                        borderRadius: 10,
                                        boxShadow: 'var(--sh-md)',
                                        padding: '6px 6px 4px',
                                        zIndex: 30,
                                        whiteSpace: 'nowrap',
                                        ...(isMe ? { right: 0 } : { left: '50%', transform: 'translateX(-50%)' }),
                                      }}
                                    >
                                      <div
                                        style={{ display: 'flex', gap: 4, alignItems: 'center' }}
                                      >
                                        {EMOJIS.map((emoji) => (
                                          <button
                                            key={emoji}
                                            onClick={(e) => {
                                              e.stopPropagation();
                                              handleReaction(m.id, emoji);
                                              setPickerMsgId(null);
                                            }}
                                            style={{
                                              border: 'none',
                                              background: 'transparent',
                                              cursor: 'pointer',
                                              fontSize: 18,
                                              padding: 2,
                                              lineHeight: 1,
                                            }}
                                          >
                                            {emoji}
                                          </button>
                                        ))}
                                        {m.reactions && Object.keys(m.reactions).length > 0 && (
                                          <>
                                            <span
                                              style={{
                                                width: 1,
                                                height: 18,
                                                background: 'var(--border)',
                                                margin: '0 2px',
                                              }}
                                            />
                                            <button
                                              onClick={async (e) => {
                                                e.stopPropagation();
                                                setPickerMsgId(null);
                                                const my = myReactionsRef.current[m.id];
                                                if (my && my.length > 0) {
                                                  const copy = [...my];
                                                  delete myReactionsRef.current[m.id];
                                                  let last: Record<string, number> | null = null;
                                                  for (const em of copy) {
                                                    try {
                                                      last = await removeReaction(m.id, em);
                                                    } catch (_) { /* silent */ }
                                                  }
                                                  if (last) {
                                                    setMessages((prev) =>
                                                      prev.map((msg) =>
                                                        msg.id === m.id ? { ...msg, reactions: last } : msg,
                                                      ),
                                                    );
                                                  }
                                                }
                                              }}
                                              style={{
                                                border: 'none',
                                                background: 'transparent',
                                                cursor: 'pointer',
                                                fontSize: 14,
                                                padding: 2,
                                                lineHeight: 1,
                                                color: 'var(--text-3)',
                                              }}
                                            >
                                              ✕
                                            </button>
                                          </>
                                        )}
                                      </div>
                                    </div>
                                  )}
                                </div>
                              </div>
                            )}
                          </div>
                          <div
                            style={{
                              flex: 'none',
                              display: 'flex',
                              flexDirection: 'row',
                              alignItems: 'center',
                              alignSelf: 'flex-end',
                              paddingBottom: 2,
                              position: 'relative',
                              gap: 8,
                              visibility:
                                !isDeleted && (hoveredId === m.id || menuMsgId === m.id + '-menu')
                                  ? 'visible'
                                  : 'hidden',
                            }}
                          >
                            {!isDeleted && menuMsgId === m.id + '-menu' && (
                              <div
                                style={{
                                  position: 'absolute',
                                  bottom: '100%',
                                  left: '50%',
                                  transform: 'translateX(-50%)',
                                  marginBottom: 6,
                                  background: '#fff',
                                  border: '1px solid var(--border)',
                                  borderRadius: 10,
                                  boxShadow: 'var(--sh-md)',
                                  padding: 4,
                                  zIndex: 30,
                                  minWidth: 100,
                                }}
                              >
                                <div
                                  className="row gap-8"
                                  style={{
                                    padding: '8px 12px',
                                    cursor: 'pointer',
                                    borderRadius: 8,
                                    fontSize: 13,
                                    color: 'var(--danger)',
                                  }}
                                  onClick={(e) => {
                                    e.stopPropagation();
                                    handleDelete(m.id, m.senderId);
                                  }}
                                >
                                  <Ic n="trash" size={14} /> Xoá
                                </div>
                              </div>
                            )}
                            {!isDeleted && (
                              <button
                                type="button"
                                onClick={(e) => {
                                  e.stopPropagation();
                                  setReplyTarget(m);
                                  inputRef.current?.focus({ preventScroll: true });
                                }}
                                style={{
                                  width: 28,
                                  height: 28,
                                  borderRadius: '50%',
                                  border: 'none',
                                  background: 'var(--surface)',
                                  boxShadow: 'var(--sh-sm)',
                                  cursor: 'pointer',
                                  display: 'grid',
                                  placeItems: 'center',
                                  color: 'var(--accent)',
                                  fontSize: 13,
                                  fontWeight: 700,
                                  padding: 0,
                                }}
                                title="Reply"
                              >
                                ↩
                              </button>
                            )}
                            {!isDeleted && m.senderId === userId && (
                              <button
                                style={{
                                  width: 28,
                                  height: 28,
                                  borderRadius: '50%',
                                  border: 'none',
                                  background: 'var(--surface)',
                                  boxShadow: 'var(--sh-sm)',
                                  cursor: 'pointer',
                                  display: 'grid',
                                  placeItems: 'center',
                                  color: 'var(--text-2)',
                                }}
                                onClick={(e) => {
                                  e.stopPropagation();
                                  setMenuMsgId((prev) =>
                                    prev === m.id + '-menu' ? null : m.id + '-menu',
                                  );
                                }}
                              >
                                <Ic n="dots" size={14} />
                              </button>
                            )}
                          </div>
                        </div>
                      </React.Fragment>
                    );
                    })}
                  </div>
                );
              });
            })()}
            <div ref={bottomRef} style={{ height: 1 }} />
          </div>

          {replyTarget && (
            <div
              style={{
                padding: '10px 16px 0',
                borderTop: '1px solid var(--border)',
                background: '#fff',
              }}
            >
              <div
                style={{
                  padding: '8px 10px',
                  borderRadius: 10,
                  background: 'var(--surface-2)',
                  border: '1px solid var(--border)',
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  gap: 8,
                }}
              >
                <div style={{ minWidth: 0 }}>
                  <div style={{ fontSize: 12, fontWeight: 700, color: 'var(--text-2)' }}>
                    Đang trả lời {replyTarget.senderName}
                  </div>
                  <div
                    style={{
                      fontSize: 12,
                      color: 'var(--text-3)',
                      overflow: 'hidden',
                      textOverflow: 'ellipsis',
                      whiteSpace: 'nowrap',
                    }}
                  >
                    {replyTarget.content || replyTarget.attachmentName || 'Tin nhắn'}
                  </div>
                </div>
                <button
                  type="button"
                  onClick={() => setReplyTarget(null)}
                  style={{
                    border: 'none',
                    background: 'transparent',
                    cursor: 'pointer',
                    color: 'var(--text-3)',
                  }}
                >
                  ✕
                </button>
              </div>
            </div>
          )}

          {error && (
            <div style={{ padding: '10px 16px', fontSize: 13, color: '#fff', background: 'var(--danger)', borderTop: '1px solid var(--border)', display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 12 }}>
              <span>{error}</span>
              <button style={{ border: 'none', background: 'transparent', color: '#fff', cursor: 'pointer', fontSize: 16, padding: 0, flex: 'none' }} onClick={() => setError('')}>✕</button>
            </div>
          )}
          <div
            className="row gap-10"
            style={{ padding: 16, borderTop: error ? 'none' : '1px solid var(--border)', background: '#fff' }}
          >
            <input ref={fileInputRef} type="file" accept=".jpg,.jpeg,.png,.gif,.pdf,.doc,.docx,.zip,.txt" style={{ display: 'none' }} onChange={handleFileSelect} />
            <button className="icon-btn" style={{ width: 44, height: 44 }} onClick={() => fileInputRef.current?.click()} disabled={sendingFile}>
              <Ic n="plus" size={20} />
            </button>
            <input
              ref={inputRef}
              className="input"
              placeholder="Nhập tin nhắn..."
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={handleKeyDown}
              disabled={!activeRoomId}
            />
            <button
              className="btn btn-primary btn-icon"
              style={{ width: 44, height: 44 }}
              onClick={handleSend}
              disabled={!input.trim() || sending}
            >
              <Ic n="send" size={18} />
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

function linkifyText(text: string): React.ReactNode {
  const urlRegex = /(https?:\/\/[^\s]+)/;
  const parts = text.split(urlRegex);
  return parts.map((part, i) => {
    if (urlRegex.test(part)) {
      return (
        <a
          key={i}
          href={part}
          target="_blank"
          rel="noopener noreferrer"
          style={{ color: '#2563eb', textDecoration: 'underline' }}
        >
          {part}
        </a>
      );
    }
    return part;
  });
}

function formatTime(value?: string) {
  if (!value) return '';
  return new Date(value).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' });
}

function formatTimeDivider(value?: string) {
  if (!value) return '';
  const d = new Date(value);
  const now = new Date();
  const hh = String(d.getHours()).padStart(2, '0');
  const mm = String(d.getMinutes()).padStart(2, '0');
  const time = `${hh}:${mm}`;
  const isToday = d.toDateString() === now.toDateString();
  if (isToday) return time + ' Hôm nay';
  const yesterday = new Date(now);
  yesterday.setDate(yesterday.getDate() - 1);
  if (d.toDateString() === yesterday.toDateString()) return time + ' Hôm qua';
  const dd = String(d.getDate()).padStart(2, '0');
  const month = String(d.getMonth() + 1).padStart(2, '0');
  const yyyy = d.getFullYear();
  return `${time} ${dd}/${month}/${yyyy}`;
}

function Ic({ n, size = 16, style }: { n: string; size?: number; style?: React.CSSProperties }) {
  return <window.Icon n={n} size={size} style={style} />;
}

function Av({ name, size = 40 }: { name: string; size?: number }) {
  return <window.Avatar name={name} size={size} />;
}

window.ChatScreen = ChatScreen;
