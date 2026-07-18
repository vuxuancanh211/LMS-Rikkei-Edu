package project.lms_rikkei_edu.modules.dashboard.util;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DashboardChartUtils {

    private static final String[] EN_MONTHS = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
    private static final String[] EN_DAYS = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};

    private DashboardChartUtils() {
    }

    public static List<String> getMonthlyLabels(int count) {
        List<String> labels = new ArrayList<>();
        Calendar cal = Calendar.getInstance();
        for (int i = count - 1; i >= 0; i--) {
            Calendar c = (Calendar) cal.clone();
            c.add(Calendar.MONTH, -i);
            labels.add(EN_MONTHS[c.get(Calendar.MONTH)]);
        }
        return labels;
    }

    public static Map<String, Integer> getYearMonthToIndexMap(int count) {
        Map<String, Integer> map = new HashMap<>();
        Calendar cal = Calendar.getInstance();
        for (int i = count - 1; i >= 0; i--) {
            Calendar c = (Calendar) cal.clone();
            c.add(Calendar.MONTH, -i);
            String ym = String.format("%04d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1);
            map.put(ym, count - 1 - i);
        }
        return map;
    }

    public static List<String> getWeeklyLabels(int count) {
        List<String> labels = new ArrayList<>();
        Calendar cal = Calendar.getInstance();
        for (int i = count - 1; i >= 0; i--) {
            Calendar c = (Calendar) cal.clone();
            c.add(Calendar.DAY_OF_YEAR, -i);
            labels.add(EN_DAYS[c.get(Calendar.DAY_OF_WEEK) - 1]);
        }
        return labels;
    }

    public static Map<String, Integer> getDateToIndexMap(int count) {
        Map<String, Integer> map = new HashMap<>();
        Calendar cal = Calendar.getInstance();
        for (int i = count - 1; i >= 0; i--) {
            Calendar c = (Calendar) cal.clone();
            c.add(Calendar.DAY_OF_YEAR, -i);
            String dateStr = String.format("%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
            map.put(dateStr, count - 1 - i);
        }
        return map;
    }
}
