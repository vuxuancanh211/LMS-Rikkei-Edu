package project.lms_rikkei_edu.modules.group.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
public class GroupResponse {
    private UUID id;
    private UUID courseId;
    private String courseTitle;
    private String name;
    private String description;
    private Integer maxCapacity;
    private int memberCount;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
    private OffsetDateTime createdAt;
}
