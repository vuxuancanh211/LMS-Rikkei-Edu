package project.lms_rikkei_edu.modules.dashboard.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Deprecated(since = "1.0", forRemoval = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemActivityDto {
    private UUID id;
    private String who;
    private String act;
    private String time;
    private String type;
}
