package project.lms_rikkei_edu.modules.forum.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class ForumCourseResponse {
    private UUID id;
    private String title;
    private boolean canCreatePost;
    private boolean canPinPost;
}
