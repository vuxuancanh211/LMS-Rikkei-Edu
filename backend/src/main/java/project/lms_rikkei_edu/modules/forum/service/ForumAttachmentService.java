package project.lms_rikkei_edu.modules.forum.service;

import org.springframework.web.multipart.MultipartFile;
import project.lms_rikkei_edu.modules.forum.dto.response.ForumAttachmentResponse;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface ForumAttachmentService {
    record AttachmentContent(byte[] bytes, String contentType, String fileName) {}

    ForumAttachmentResponse upload(MultipartFile file);

    AttachmentContent getContent(UUID attachmentId, String token);

    int cleanupOrphanAttachments();

    void attachToPost(List<UUID> attachmentIds, UUID postId, UUID uploaderId);

    void attachToReply(List<UUID> attachmentIds, UUID replyId, UUID uploaderId);

    Map<UUID, List<ForumAttachmentResponse>> findByPostIds(List<UUID> postIds);

    Map<UUID, List<ForumAttachmentResponse>> findByReplyIds(List<UUID> replyIds);
}
