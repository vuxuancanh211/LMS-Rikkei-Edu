package project.lms_rikkei_edu.common.dto.response;

import lombok.Getter;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Getter
public class PagedResponse<T> {

    private final List<T> items;
    private final long totalRecords;
    private final int totalPages;
    private final int page;
    private final int size;

    public PagedResponse(List<T> items, long totalRecords, int page, int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("size must be positive");
        }
        this.items = items != null ? items : Collections.emptyList();
        this.totalRecords = Math.max(totalRecords, 0);
        this.page = Math.max(page, 0);
        this.size = size;
        this.totalPages = (int) Math.ceil((double) this.totalRecords / size);
    }
}
