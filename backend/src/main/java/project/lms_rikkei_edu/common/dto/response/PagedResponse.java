package project.lms_rikkei_edu.common.dto.response;

import lombok.Getter;

import java.util.List;

@Getter
public class PagedResponse<T> {

    private final List<T> items;
    private final long totalRecords;
    private final int totalPages;
    private final int page;
    private final int size;

    public PagedResponse(List<T> items, long totalRecords, int page, int size) {
        this.items = items;
        this.totalRecords = totalRecords;
        this.page = page;
        this.size = size;
        this.totalPages = (int) Math.ceil((double) totalRecords / size);
    }
}
