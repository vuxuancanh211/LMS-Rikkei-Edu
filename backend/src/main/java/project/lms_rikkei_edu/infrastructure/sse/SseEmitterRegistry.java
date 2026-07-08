package project.lms_rikkei_edu.infrastructure.sse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
@Slf4j
public class SseEmitterRegistry {

    private final Map<UUID, CopyOnWriteArrayList<SseEmitter>> registry =
            new ConcurrentHashMap<>();

    public SseEmitter register(UUID userId) {
        SseEmitter emitter = new SseEmitter(0L);
        registry.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>())
                .add(emitter);

        Runnable cleanup = () -> remove(userId, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());
        return emitter;
    }

    // ── Gửi đến 1 user (tất cả tab) ──────────────────────

    public void sendToUser(UUID userId, String eventType, Object data) {
        List<SseEmitter> emitters = registry.get(userId);
        if (emitters == null || emitters.isEmpty()) return;
        dispatch(emitters, eventType, data);
    }

    // ── Broadcast đến nhiều user ──────────────────────────

    public void sendToUsers(List<UUID> userIds, String eventType, Object data) {
        userIds.forEach(userId -> sendToUser(userId, eventType, data));
    }

    public void broadcast(String eventType, Object data) {
        registry.keySet().forEach(userId -> sendToUser(userId, eventType, data));
    }

    // ── Heartbeat — gọi từ scheduler mỗi 30 giây ─────────

    @Scheduled(fixedDelay = 30_000)
    public void heartbeat() {
        if (registry.isEmpty()) return;
        registry.keySet().forEach(userId ->
                sendToUser(userId, "HEARTBEAT", "ping"));
    }

    public boolean isConnected(UUID userId) {
        List<SseEmitter> emitters = registry.get(userId);
        return emitters != null && !emitters.isEmpty();
    }

    // ── Internal ──────────────────────────────────────────

    private void dispatch(List<SseEmitter> emitters,
                          String eventType, Object data) {
        List<SseEmitter> dead = new ArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name(eventType)
                        .data(data));
            } catch (Exception e) {
                dead.add(emitter);
            }
        }
        emitters.removeAll(dead);
    }

    private void remove(UUID userId, SseEmitter emitter) {
        List<SseEmitter> emitters = registry.get(userId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) registry.remove(userId);
        }
    }
}
