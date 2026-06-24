package project.lms_rikkei_edu.modules.forum.service;

import org.springframework.web.multipart.MultipartFile;
import project.lms_rikkei_edu.modules.forum.dto.response.ForumAttachmentResponse;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface ForumAttachmentService {
    record AttachmentContent(byte[] bytes, String contentType, String fileName) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof AttachmentContent that)) return false;
            return Arrays.equals(bytes, that.bytes)
                    && contentType.equals(that.contentType)
                    && fileName.equals(that.fileName);
        }

        @Override
        public int hashCode() {
            int result = Arrays.hashCode(bytes);
            result = 31 * result + contentType.hashCode();
            result = 31 * result + fileName.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return "AttachmentContent[bytes=" + Arrays.toString(bytes)
                    + ", contentType=" + contentType
                    + ", fileName=" + fileName + "]";
        }
    }

    ForumAttachmentResponse upload(MultipartFile file);

    AttachmentContent getContent(UUID attachmentId, String token);

    int cleanupOrphanAttachments();

    void attachToPost(List<UUID> attachmentIds, UUID postId, UUID uploaderId);

    void attachToReply(List<UUID> attachmentIds, UUID replyId, UUID uploaderId);

    Map<UUID, List<ForumAttachmentResponse>> findByPostIds(List<UUID> postIds);

    Map<UUID, List<ForumAttachmentResponse>> findByReplyIds(List<UUID> replyIds);
}
