package project.lms_rikkei_edu.modules.chat.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import project.lms_rikkei_edu.common.exception.BusinessException;
import project.lms_rikkei_edu.common.security.CurrentUserProvider;
import project.lms_rikkei_edu.modules.chat.dto.request.MarkAsReadRequest;
import project.lms_rikkei_edu.modules.chat.dto.response.ChatRoomResponse;
import project.lms_rikkei_edu.modules.chat.service.ChatRoomService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/chat/rooms")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class ChatRoomController {

    private final ChatRoomService chatRoomService;
    private final CurrentUserProvider currentUserProvider;

    private UUID currentUserId() {
        return currentUserProvider.getCurrentUserId()
                .orElseThrow(() -> new BusinessException("Unauthorized", HttpStatus.UNAUTHORIZED));
    }

    @GetMapping
    public ResponseEntity<List<ChatRoomResponse>> getMyRooms() {
        return ResponseEntity.ok(chatRoomService.getMyRooms(currentUserId()));
    }

    @GetMapping("/{roomId}")
    public ResponseEntity<ChatRoomResponse> getRoomDetail(@PathVariable UUID roomId) {
        return ResponseEntity.ok(chatRoomService.getRoomDetail(roomId, currentUserId()));
    }

    @PostMapping("/{roomId}/read")
    public ResponseEntity<Void> markAsRead(
            @PathVariable UUID roomId,
            @RequestBody @Valid MarkAsReadRequest request) {
        chatRoomService.markAsRead(roomId, request.getMessageId(), currentUserId());
        return ResponseEntity.ok().build();
    }
}
