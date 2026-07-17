package project.lms_rikkei_edu.modules.notification.dto.response;

import lombok.Value;
import org.springframework.data.domain.Page;

import java.util.List;

@Value
public class NotificationPageResponse<T> {
    List<T> content;
    int page;
    int size;
    long totalElements;
    int totalPages;
    boolean last;

    public static <T> NotificationPageResponse<T> from(Page<T> page) {
        return new NotificationPageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast()
        );
    }
}
