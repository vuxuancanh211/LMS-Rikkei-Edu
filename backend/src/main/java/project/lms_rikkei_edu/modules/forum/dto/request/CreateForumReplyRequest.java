package project.lms_rikkei_edu.modules.forum.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class CreateForumReplyRequest {

    @NotBlank(message = "Content is required")
    private String content;

    private UUID parentReplyId;
}
