package project.lms_rikkei_edu.modules.chat.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import project.lms_rikkei_edu.common.exception.BusinessException;
import project.lms_rikkei_edu.common.security.CurrentUserProvider;
import project.lms_rikkei_edu.infrastructure.s3.S3Service;
import project.lms_rikkei_edu.modules.chat.dto.request.EditMessageRequest;
import project.lms_rikkei_edu.modules.chat.dto.request.PresignUploadRequest;
import project.lms_rikkei_edu.modules.chat.dto.request.ReactMessageRequest;
import project.lms_rikkei_edu.modules.chat.dto.request.SendMessageRequest;
import project.lms_rikkei_edu.modules.chat.dto.response.AttachmentPresignResponse;
import project.lms_rikkei_edu.modules.chat.dto.response.ChatMessageResponse;
import project.lms_rikkei_edu.modules.chat.service.ChatMessageService;
import project.lms_rikkei_edu.modules.chat.service.ChatRoomService;
import project.lms_rikkei_edu.modules.user.entity.UserEntity;
import project.lms_rikkei_edu.modules.user.repository.UserRepository;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class ChatMessageController {

    private final ChatMessageService chatMessageService;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;
    private final ChatRoomService chatRoomService;
    private final S3Service s3Service;
    private static final Set<String> ALLOWED_EXTENSIONS =
            Set.of("jpg", "jpeg", "png", "gif", "pdf", "doc", "docx", "zip", "txt");

    private UUID currentUserId() {
        return currentUserProvider.getCurrentUserId()
                .orElseThrow(() -> new BusinessException("Unauthorized", HttpStatus.UNAUTHORIZED));
    }

    private UserEntity currentUser() {
        return userRepository.findById(currentUserId())
                .orElseThrow(() -> new BusinessException("User not found", HttpStatus.UNAUTHORIZED));
    }

    @GetMapping("/rooms/{roomId}/messages")
    public ResponseEntity<Page<ChatMessageResponse>> getMessages(
            @PathVariable UUID roomId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(
                chatMessageService.getMessages(roomId, currentUserId(), pageable));
    }

    @PostMapping("/rooms/{roomId}/messages")
    public ResponseEntity<ChatMessageResponse> sendMessage(
            @PathVariable UUID roomId,
            @Valid @RequestBody SendMessageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(chatMessageService.sendMessage(roomId, request, currentUser()));
    }

    @PutMapping("/messages/{messageId}")
    public ResponseEntity<ChatMessageResponse> editMessage(
            @PathVariable UUID messageId,
            @Valid @RequestBody EditMessageRequest request) {
        return ResponseEntity.ok(
                chatMessageService.editMessage(messageId, request, currentUserId()));
    }

    @DeleteMapping("/messages/{messageId}")
    public ResponseEntity<Void> deleteMessage(@PathVariable UUID messageId) {
        chatMessageService.deleteMessage(messageId, currentUserId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/messages/{messageId}/reactions")
    public ResponseEntity<Map<String, Long>> addReaction(
            @PathVariable UUID messageId,
            @Valid @RequestBody ReactMessageRequest request) {
        return ResponseEntity.ok(
                chatMessageService.addReaction(messageId, request, currentUser()));
    }

    @DeleteMapping("/messages/{messageId}/reactions")
    public ResponseEntity<Map<String, Long>> removeReaction(
            @PathVariable UUID messageId,
            @RequestParam String emoji) {
        return ResponseEntity.ok(
                chatMessageService.removeReaction(messageId, emoji, currentUserId()));
    }

    @PostMapping("/rooms/{roomId}/attachments/presign-upload")
    public ResponseEntity<AttachmentPresignResponse> presignUpload(
            @PathVariable UUID roomId,
            @Valid @RequestBody PresignUploadRequest request) {
        // validate member
        chatRoomService.validateMember(roomId, currentUserId());
        // gen key
        String originalName = request.getFileName();
        int dotIndex = originalName.lastIndexOf('.');
        String ext = (dotIndex > 0) ? originalName.substring(dotIndex + 1) : "bin";
        if (!ALLOWED_EXTENSIONS.contains(ext.toLowerCase())) {
            throw new BusinessException("Định dạng file không được hỗ trợ");
        }
        String s3Key = "chat/" + roomId + "/" + UUID.randomUUID() + "." + ext;
        String uploadUrl = s3Service.generatePresignedPutUrl(s3Key, request.getMimeType()).url().toString();
        String viewUrl = s3Service.generatePresignedInlineUrl(s3Key, 86400).url().toString();
        return ResponseEntity.ok(new AttachmentPresignResponse(uploadUrl, viewUrl, s3Key));
    }
}
