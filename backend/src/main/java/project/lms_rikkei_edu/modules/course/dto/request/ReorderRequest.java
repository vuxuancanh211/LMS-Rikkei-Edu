package project.lms_rikkei_edu.modules.course.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class ReorderRequest {

    /** Toàn bộ id (chapter hoặc lesson) theo đúng thứ tự mới mong muốn — phải khớp chính xác tập id hiện có. */
    @NotEmpty
    private List<UUID> ids;
}
