package project.lms_rikkei_edu.modules.forum.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import project.lms_rikkei_edu.modules.forum.dto.response.ForumAttachmentResponse;
import project.lms_rikkei_edu.modules.forum.service.ForumAttachmentService;

import java.util.UUID;

@RestController
@RequestMapping("/api/forum/attachments")
@RequiredArgsConstructor
public class ForumAttachmentController {

    private final ForumAttachmentService forumAttachmentService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ForumAttachmentResponse> upload(@RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(forumAttachmentService.upload(file));
    }

    @GetMapping("/{attachmentId}/content")
    public ResponseEntity<byte[]> content(@PathVariable UUID attachmentId, @RequestParam String token) {
        ForumAttachmentService.AttachmentContent content = forumAttachmentService.getContent(attachmentId, token);
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(content.fileName())
                .build();

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(content.bytes());
    }
}
