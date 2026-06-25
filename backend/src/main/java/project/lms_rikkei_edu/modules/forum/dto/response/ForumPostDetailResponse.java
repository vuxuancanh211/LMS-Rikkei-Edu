package project.lms_rikkei_edu.modules.forum.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ForumPostDetailResponse {
    private ForumPostResponse post;
    private List<ForumReplyResponse> replies;
}
