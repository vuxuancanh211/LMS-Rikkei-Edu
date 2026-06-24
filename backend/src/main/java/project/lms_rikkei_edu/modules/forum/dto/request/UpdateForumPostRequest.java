package project.lms_rikkei_edu.modules.forum.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class UpdateForumPostRequest {

    @NotBlank(message = "Topic is required")
    @Size(max = 30, message = "Topic must not exceed 30 characters")
    private String topic;

    @NotBlank(message = "Title is required")
    @Size(max = 200, message = "Title must not exceed 200 characters")
    private String title;

    @NotBlank(message = "Content is required")
    private String content;

    private Boolean pinned;

    private List<UUID> attachmentIds;
}
