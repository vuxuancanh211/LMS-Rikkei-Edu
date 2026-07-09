package project.lms_rikkei_edu.modules.course.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class StudentCourseResponse {
    private UUID id;
    private String title;
    private String thumbnailUrl;
    private String category;
    private String instructor;
    private int lessons;
    private int hours;
    private String level;
    private double rating;
    private int progress;
    private String sStatus;
    private String pubStatus;
    private int chapters;
}
