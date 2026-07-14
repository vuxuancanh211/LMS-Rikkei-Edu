package project.lms_rikkei_edu.infrastructure.sse;

import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockConstruction;

class SseEmitterRegistryTest {

    @Test
    void registerTracksConnectionAndCompletionRemovesIt() {
        SseEmitterRegistry registry = new SseEmitterRegistry();
        UUID userId = UUID.randomUUID();

        SseEmitter emitter = registry.register(userId);

        assertThat(registry.isConnected(userId)).isTrue();

        ReflectionTestUtils.invokeMethod(registry, "remove", userId, emitter);

        assertThat(registry.isConnected(userId)).isFalse();
    }

    @Test
    void sendToUserAndHeartbeatKeepLiveEmitterRegistered() {
        SseEmitterRegistry registry = new SseEmitterRegistry();
        UUID userId = UUID.randomUUID();
        registry.register(userId);

        registry.sendToUser(userId, "EVENT", "data");
        registry.heartbeat();

        assertThat(registry.isConnected(userId)).isTrue();
    }

    @Test
    void sendMethodsAreNoopWhenThereAreNoEmitters() {
        SseEmitterRegistry registry = new SseEmitterRegistry();
        UUID userId = UUID.randomUUID();

        registry.sendToUser(userId, "EVENT", "data");
        registry.sendToUsers(List.of(userId), "EVENT", "data");
        registry.broadcast("EVENT", "data");
        registry.heartbeat();

        assertThat(registry.isConnected(userId)).isFalse();
    }

    @Test
    void registerRemovesConnection_whenInitialEventCannotBeSent() {
        SseEmitterRegistry registry = new SseEmitterRegistry();
        UUID userId = UUID.randomUUID();

        try (MockedConstruction<SseEmitter> ignored = mockConstruction(SseEmitter.class, (mock, context) ->
                doThrow(new IOException("client disconnected")).when(mock).send(any(SseEmitter.SseEventBuilder.class)))) {
            registry.register(userId);
        }

        assertThat(registry.isConnected(userId)).isFalse();
    }
}
