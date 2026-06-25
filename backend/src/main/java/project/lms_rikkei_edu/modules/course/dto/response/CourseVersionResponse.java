package project.lms_rikkei_edu.modules.course.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class CourseVersionResponse {
    private UUID id;
    private Integer versionNumber;
    /** DRAFT | PENDING | APPROVED | REJECTED */
    private String status;
    private String changeSummary;
    private String rejectionReason;
    /** Tên tùy chọn do instructor đặt khi lưu bản nháp DRAFT */
    private String label;
    private Instant submittedAt;
    private Instant reviewedAt;
    /** JSON snapshot — dùng để xem nội dung version hoặc rollback */
    private String snapshot;
}
