import { useAuthStore } from '../store';
import { httpClient } from '../lib';

export type NotificationItem = {
  id: string;
  recipientId: string;
  type: string;
  title: string;
  body?: string | null;
  referenceType?: string | null;
  referenceId?: string | null;
  actorId?: string | null;
  actorName?: string | null;
  priority: string;
  read: boolean;
  createdAt: string;
};

export type NotificationPageResponse<T> = {
  content: T[];
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export async function getNotifications(page = 0, size = 20) {
  const response = await httpClient.get<NotificationPageResponse<NotificationItem>>('/notifications', { params: { page, size } });
  return response.data;
}

export async function getUnreadCount() {
  const response = await httpClient.get<{ count: number }>('/notifications/unread-count');
  return response.data.count;
}

export async function markAsRead(id: string) {
  await httpClient.patch(`/notifications/${id}/read`);
}

export async function markAllAsRead() {
  await httpClient.patch('/notifications/read-all');
}

export function connectNotificationSSE(
  onNotification: (notif: NotificationItem) => void,
  onError?: () => void,
  onReconnect?: () => void,
): () => void {
  const baseURL = httpClient.defaults.baseURL || '';

  const url = `${baseURL}/notifications/sse`;
  const reconnectDelayMs = 3000;
  let aborted = false;
  let reader: ReadableStreamDefaultReader<Uint8Array> | null = null;
  let reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  let hasConnected = false;

  function parseEvent(part: string) {
    let eventName = 'message';
    const dataLines: string[] = [];

    for (const line of part.split(/\r?\n/)) {
      if (!line || line.startsWith(':')) continue;
      const separatorIndex = line.indexOf(':');
      const field = separatorIndex === -1 ? line : line.slice(0, separatorIndex);
      let value = separatorIndex === -1 ? '' : line.slice(separatorIndex + 1);
      if (value.startsWith(' ')) value = value.slice(1);

      if (field === 'event') eventName = value;
      if (field === 'data') dataLines.push(value);
    }

    if (eventName !== 'NOTIFICATION' || dataLines.length === 0) return;

    try {
      const parsed = JSON.parse(dataLines.join('\n'));
      if (parsed.notification) {
        onNotification(parsed.notification);
      }
    } catch { /* ignore malformed SSE payloads */ }
  }

  function scheduleReconnect() {
    if (aborted || reconnectTimer) return;
    reconnectTimer = setTimeout(() => {
      reconnectTimer = null;
      connect();
    }, reconnectDelayMs);
  }

  async function connect() {
    const { accessToken, tokenType } = useAuthStore.getState();
    if (!accessToken) return;

    try {
      const response = await fetch(url, {
        headers: { 'Authorization': `${tokenType || 'Bearer'} ${accessToken}` },
      });
      if (!response.ok || !response.body) {
        if (!aborted && onError) onError();
        return;
      }

      reader = response.body.getReader();
      if (hasConnected && onReconnect) onReconnect();
      hasConnected = true;
      const decoder = new TextDecoder();
      let buffer = '';

      while (true) {
        const { done, value } = await reader.read();
        if (done || aborted) break;
        buffer += decoder.decode(value, { stream: true });

        const parts = buffer.split('\n\n');
        buffer = parts.pop() || '';

        for (const part of parts) {
          parseEvent(part);
        }
      }
    } catch {
      if (!aborted && onError) onError();
    } finally {
      reader = null;
      scheduleReconnect();
    }
  }

  connect();

  return () => {
    aborted = true;
    if (reconnectTimer) clearTimeout(reconnectTimer);
    if (reader) reader.cancel();
  };
}
