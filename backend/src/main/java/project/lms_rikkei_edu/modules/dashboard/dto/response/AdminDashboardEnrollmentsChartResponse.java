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
public class AdminDashboardEnrollmentsChartResponse {
    private List<Integer> enrollmentsData;
    private List<String> enrollmentsLabels;
    private List<Integer> weeklyEnrollmentsData;
    private List<String> weeklyEnrollmentsLabels;
}
