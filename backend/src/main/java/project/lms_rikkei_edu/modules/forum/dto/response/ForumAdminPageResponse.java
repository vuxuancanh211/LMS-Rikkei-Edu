package project.lms_rikkei_edu.modules.forum.dto.response;

import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

@Getter
@Builder
public class ForumAdminPageResponse<T> {
    private List<T> content;
    private int number;
    private int size;
    private long totalElements;
    private int totalPages;

    public static <T> ForumAdminPageResponse<T> from(Page<T> page) {
        return ForumAdminPageResponse.<T>builder()
                .content(page.getContent())
                .number(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .build();
    }
}
