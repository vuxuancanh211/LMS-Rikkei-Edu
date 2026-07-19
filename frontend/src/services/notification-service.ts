import { useAuthStore } from '../store';
import { httpClient, refreshAccessToken } from '../lib';

export type NotificationItem = {
  id: string;
  type: string;
  title: string;
  body?: string | null;
  referenceType?: string | null;
  referenceId?: string | null;
  read: boolean;
  createdAt: string;
};

export type NotificationPageResponse<T> = {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
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

type SSESubscriber = {
  onEvent: (eventName: string, data: unknown) => void;
  onError?: () => void;
  onReconnect?: () => void;
};

const sseSubscribers = new Set<SSESubscriber>();
let sseDelayMs = 3000;
let sseAborted = false;
let sseReader: ReadableStreamDefaultReader<Uint8Array> | null = null;
let sseController: AbortController | null = null;
let sseReconnectTimer: ReturnType<typeof setTimeout> | null = null;
let sseHasConnected = false;
let sseRunning = false;

async function refreshSseAuthentication() {
  try {
    await refreshAccessToken();
    return !!useAuthStore.getState().accessToken;
  } catch {
    return false;
  }
}

export function connectSSE(
  onEvent: (eventName: string, data: unknown) => void,
  onError?: () => void,
  onReconnect?: () => void,
): () => void {
  const baseURL = httpClient.defaults.baseURL || '';
  const url = `${baseURL}/notifications/sse`;

  const subscriber: SSESubscriber = { onEvent, onError, onReconnect };
  sseSubscribers.add(subscriber);
  sseAborted = false;

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
      notifySubscribers(eventName, null);
      return;
    }

    try {
      notifySubscribers(eventName, JSON.parse(dataLines.join('\n')));
    } catch {
      notifySubscribers(eventName, dataLines.join('\n'));
    }
  }

  function notifySubscribers(eventName: string, data: unknown) {
    [...sseSubscribers].forEach((item) => item.onEvent(eventName, data));
  }

  function notifyError() {
    [...sseSubscribers].forEach((item) => item.onError?.());
  }

  function notifyReconnect() {
    [...sseSubscribers].forEach((item) => item.onReconnect?.());
  }

  function scheduleReconnect() {
    if (sseAborted || sseSubscribers.size === 0 || sseReconnectTimer) return;
    sseReconnectTimer = setTimeout(() => {
      sseReconnectTimer = null;
      connect();
    }, sseDelayMs);
  }

  async function connect() {
    if (sseRunning || sseSubscribers.size === 0) return;

    const { accessToken, tokenType } = useAuthStore.getState();
    if (!accessToken) return;

    sseRunning = true;
    try {
      sseController = new AbortController();
      const response = await fetch(url, {
        headers: { 'Authorization': `${tokenType || 'Bearer'} ${accessToken}` },
        signal: sseController.signal,
      });
      if (response.status === 401) {
        const refreshed = await refreshSseAuthentication();
        if (refreshed) {
          sseDelayMs = 500;
        } else {
          sseAborted = true;
          if (!sseAborted) notifyError();
        }
        return;
      }
      if (!response.ok || !response.body) {
        sseDelayMs = Math.min(sseDelayMs * 1.5, 30000);
        if (!sseAborted) notifyError();
        return;
      }

      sseReader = response.body.getReader();
      if (sseHasConnected) notifyReconnect();
      sseHasConnected = true;
      sseDelayMs = 3000;
      const decoder = new TextDecoder();
      let buffer = '';

      while (true) {
        const { done, value } = await sseReader.read();
        if (done || sseAborted) break;
        buffer += decoder.decode(value, { stream: true });

        const parts = buffer.split('\n\n');
        buffer = parts.pop() || '';

        for (const part of parts) {
          parseEvent(part);
        }
      }
    } catch {
      sseDelayMs = Math.min(sseDelayMs * 1.5, 30000);
      if (!sseAborted) notifyError();
    } finally {
      sseRunning = false;
      sseReader = null;
      sseController = null;
      scheduleReconnect();
    }
  }

  connect();

  return () => {
    sseSubscribers.delete(subscriber);
    if (sseSubscribers.size === 0) {
      sseAborted = true;
      sseHasConnected = false;
      sseDelayMs = 3000;
      if (sseReconnectTimer) {
        clearTimeout(sseReconnectTimer);
        sseReconnectTimer = null;
      }
      if (sseController) sseController.abort();
      if (sseReader) {
        sseReader.cancel().catch(() => {
          // Ignore expected AbortError when React unmounts the SSE stream.
        });
      }
    }
  };
}

export function connectNotificationSSE(
  onNotification: (notif: NotificationItem) => void,
  onError?: () => void,
  onReconnect?: () => void,
  onUnreadCount?: (count: number) => void,
  onLatestNotifications?: (notifications: NotificationItem[]) => void,
): () => void {
  return connectSSE((eventName, data) => {
    if (eventName === 'ACCOUNT_LOCKED') {
      if (typeof window !== 'undefined' && (window as any).__triggerAccountLockedModal) {
        (window as any).__triggerAccountLockedModal(data);
      }
      return;
    }
    if (eventName === 'UNREAD_COUNT' && data && typeof data === 'object') {
      const count = (data as { count?: unknown }).count;
      if (typeof count === 'number') onUnreadCount?.(count);
      return;
    }
    if (eventName === 'LATEST_NOTIFICATIONS' && data && typeof data === 'object') {
      const notifications = (data as { notifications?: unknown }).notifications;
      if (Array.isArray(notifications)) onLatestNotifications?.(notifications as NotificationItem[]);
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
