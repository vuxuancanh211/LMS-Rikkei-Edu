package project.lms_rikkei_edu.modules.group.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
public class GroupMemberResponse {
    private UUID id;
    private UUID studentId;
    private String studentName;
    private String studentEmail;
    private String avatarUrl;
    private OffsetDateTime joinedAt;
}
