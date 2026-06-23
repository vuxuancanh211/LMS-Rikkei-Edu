package project.lms_rikkei_edu.modules.forum.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class ForumAuthorResponse {
    private UUID id;
    private String fullName;
    private String role;
    private String avatarUrl;
}
