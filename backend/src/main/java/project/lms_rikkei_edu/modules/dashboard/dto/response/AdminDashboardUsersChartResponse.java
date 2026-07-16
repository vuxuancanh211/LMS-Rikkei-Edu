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
public class AdminDashboardUsersChartResponse {
    private List<Integer> newUsersData;
    private List<String> newUsersLabels;
    private List<Integer> weeklyUsersData;
    private List<String> weeklyUsersLabels;
}
