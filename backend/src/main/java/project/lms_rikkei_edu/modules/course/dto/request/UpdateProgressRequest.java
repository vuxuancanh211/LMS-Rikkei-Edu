package project.lms_rikkei_edu.modules.course.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class UpdateProgressRequest {
    private BigDecimal watchedPercentage;
    private Integer lastPlaybackPosition;
    private Integer documentViewSeconds;
}
