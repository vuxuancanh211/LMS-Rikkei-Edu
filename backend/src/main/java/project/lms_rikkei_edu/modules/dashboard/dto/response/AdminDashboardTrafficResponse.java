package project.lms_rikkei_edu.modules.dashboard.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminDashboardTrafficResponse {
    private List<Double> trafficData;
    private List<String> trafficLabels;
    private List<Double> weeklyTrafficData;
    private List<String> weeklyTrafficLabels;
}
