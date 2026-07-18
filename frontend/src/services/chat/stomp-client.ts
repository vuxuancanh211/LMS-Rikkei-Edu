import { Client, IMessage } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { useAuthStore } from '../../store';

let stompClient: Client | null = null;
let subscribedRooms = new Map<string, () => void>();
let connectCallbacks: (() => void)[] = [];

const wsBaseUrl = import.meta.env.VITE_WS_BASE_URL || '';

export function getStompClient(): Client | null {
    return stompClient;
}

export function isConnected(): boolean {
    return stompClient?.connected ?? false;
}

export function connectStomp(
    onConnect?: () => void,
    onError?: (msg: string) => void
): void {
    const token = useAuthStore.getState().accessToken;
    if (!token) return;
    if (stompClient?.connected) {
        onConnect?.();
        return;
    }

    stompClient = new Client({
        webSocketFactory: () => new SockJS(`${wsBaseUrl}/ws`),
        connectHeaders: { Authorization: `Bearer ${token}` },
        // beforeConnect chạy lại trước MỖI lần kết nối, kể cả các lần tự động reconnect do
        // reconnectDelay kích hoạt (mất mạng, backend restart, token đã refresh...) — nếu không
        // đọc lại token mới nhất ở đây, connectHeaders sẽ mãi mãi giữ token cũ từ lúc tạo Client,
        // khiến reconnect lặp vô hạn mà không bao giờ thành công cho tới khi F5 lại trang.
        beforeConnect: () => {
            const latestToken = useAuthStore.getState().accessToken;
            if (stompClient && latestToken) {
                stompClient.connectHeaders = { Authorization: `Bearer ${latestToken}` };
            }
        },
        reconnectDelay: 5000,
        heartbeatIncoming: 10000,
        heartbeatOutgoing: 10000,
        onConnect: () => {
            connectCallbacks.forEach(fn => fn());
            onConnect?.();
        },
        onStompError: (frame) => {
            onError?.(frame.headers['message'] || 'STOMP error');
        },
        onWebSocketClose: () => {},
    });

    stompClient.activate();
}

export function disconnectStomp(): void {
    subscribedRooms.forEach(unsub => unsub());
    subscribedRooms.clear();
    stompClient?.deactivate();
    stompClient = null;
    connectCallbacks = [];
}

export function onReconnect(fn: () => void): () => void {
    connectCallbacks.push(fn);
    return () => {
        connectCallbacks = connectCallbacks.filter(cb => cb !== fn);
    };
}

export function subscribeRoom(
    roomId: string,
    onMessage: (msg: IMessage) => void
): void {
    if (!stompClient?.connected) return;
    unsubscribeRoom(roomId);

    const subscription = stompClient.subscribe(
        `/topic/chat.${roomId}`,
        (message) => { onMessage(message); }
    );

    subscribedRooms.set(roomId, () => subscription.unsubscribe());
}

export function unsubscribeRoom(roomId: string): void {
    subscribedRooms.get(roomId)?.();
    subscribedRooms.delete(roomId);
}

export function sendMessageViaStomp(
    roomId: string,
    payload: Record<string, unknown>
): boolean {
    if (!stompClient?.connected) return false;

    stompClient.publish({
        destination: `/app/chat.send.${roomId}`,
        body: JSON.stringify(payload),
    });

    return true;
}
