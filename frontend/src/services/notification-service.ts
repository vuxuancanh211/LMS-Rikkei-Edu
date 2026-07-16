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

export type NotificationPreference = {
  id: string | null;
  type: string;
  inAppEnabled: boolean;
  emailEnabled: boolean;
  pushEnabled: boolean;
};

export type UpdateNotificationPreference = {
  inAppEnabled: boolean;
  emailEnabled: boolean;
  pushEnabled: boolean;
};

export async function getNotifications(page = 0, size = 20): Promise<NotificationPageResponse<NotificationItem>> {
  const response = await httpClient.get<NotificationPageResponse<NotificationItem>>('/notifications', { params: { page, size } });
  return response.data;
}

export async function getUnreadCount(): Promise<number> {
  const response = await httpClient.get<{ count: number }>('/notifications/unread-count');
  return response.data.count;
}

export async function markAsRead(id: string): Promise<void> {
  await httpClient.patch(`/notifications/${id}/read`);
}

export async function markAllAsRead(): Promise<void> {
  await httpClient.patch('/notifications/read-all');
}

export async function getNotificationPreferences(): Promise<NotificationPreference[]> {
  const response = await httpClient.get<NotificationPreference[]>('/notifications/preferences');
  return response.data;
}

export async function updateNotificationPreference(
  type: string,
  data: UpdateNotificationPreference
): Promise<NotificationPreference> {
  const response = await httpClient.put<NotificationPreference>(`/notifications/preferences/${type}`, data);
  return response.data;
}

export function connectSSE(
  onEvent: (eventName: string, data: unknown) => void,
  onError?: () => void,
  onReconnect?: () => void,
): () => void {
  const baseURL = httpClient.defaults.baseURL || '';

  const url = `${baseURL}/notifications/sse`;
  let currentDelayMs = 3000;
  let aborted = false;
  let reader: ReadableStreamDefaultReader<Uint8Array> | null = null;
  let controller: AbortController | null = null;
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

    if (dataLines.length === 0) {
      onEvent(eventName, null);
      return;
    }

    try {
      onEvent(eventName, JSON.parse(dataLines.join('\n')));
    } catch {
      onEvent(eventName, dataLines.join('\n'));
    }
  }

  function scheduleReconnect() {
    if (aborted || reconnectTimer) return;
    reconnectTimer = setTimeout(() => {
      reconnectTimer = null;
      connect();
    }, currentDelayMs);
  }

  async function connect() {
    const { accessToken, tokenType } = useAuthStore.getState();
    if (!accessToken) return;

    try {
      controller = new AbortController();
      const response = await fetch(url, {
        headers: { 'Authorization': `${tokenType || 'Bearer'} ${accessToken}` },
        signal: controller.signal,
      });
      if (!response.ok || !response.body) {
        currentDelayMs = Math.min(currentDelayMs * 1.5, 30000);
        if (!aborted && onError) onError();
        return;
      }

      reader = response.body.getReader();
      if (hasConnected && onReconnect) onReconnect();
      hasConnected = true;
      currentDelayMs = 3000;
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
      currentDelayMs = Math.min(currentDelayMs * 1.5, 30000);
      if (!aborted && onError) onError();
    } finally {
      reader = null;
      controller = null;
      scheduleReconnect();
    }
  }

  connect();

  return () => {
    aborted = true;
    if (reconnectTimer) clearTimeout(reconnectTimer);
    if (controller) controller.abort();
    if (reader) {
      reader.cancel().catch(() => {
        // Ignore expected AbortError when React unmounts the SSE stream.
      });
    }
  };
}

export function connectNotificationSSE(
  onNotification: (notif: NotificationItem) => void,
  onError?: () => void,
  onReconnect?: () => void,
): () => void {
  return connectSSE((eventName, data) => {
    if (eventName === 'ACCOUNT_LOCKED') {
      if (typeof window !== 'undefined' && (window as any).__triggerAccountLockedModal) {
        (window as any).__triggerAccountLockedModal(data);
      }
      return;
    }
    if (eventName !== 'NOTIFICATION' || !data || typeof data !== 'object') return;
    const notification = (data as { notification?: NotificationItem }).notification;
    if (notification) onNotification(notification);
  }, onError, onReconnect);
}

export function connectAccountLockedSSE(
  onLocked: (data: unknown) => void,
  onError?: () => void,
  onReconnect?: () => void,
): () => void {
  return connectSSE((eventName, data) => {
    if (eventName === 'ACCOUNT_LOCKED') {
      onLocked(data);
    }
  }, onError, onReconnect);
}
