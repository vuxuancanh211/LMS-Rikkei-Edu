package project.lms_rikkei_edu.infrastructure.sse;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
public class SSETest {
    private final SseEmitterRegistry sseEmitterRegistry;

    @GetMapping(value = "/sse/connect/{userId}",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter connect(@PathVariable UUID userId) {

        return sseEmitterRegistry.register(userId);
    }

    @PostMapping("/sse/send/{userId}")
    public String send(
            @PathVariable UUID userId,
            @RequestParam String message) {

        sseEmitterRegistry.sendToUser(
                userId,
                "NOTIFICATION",
                message
        );

        return "Sent";
    }
}
