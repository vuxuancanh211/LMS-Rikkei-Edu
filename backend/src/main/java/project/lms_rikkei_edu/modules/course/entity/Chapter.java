package project.lms_rikkei_edu.modules.course.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "chapters")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Chapter {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 1000)
    private String description;

    @Column(name = "order_index", nullable = false)
    private Integer orderIndex;

    /** true = chương này vừa được tạo, chưa được admin duyệt */
    @Column(name = "is_draft", nullable = false)
    @Builder.Default
    private Boolean isDraft = false;

    /** true = instructor muốn xóa chương này, chờ admin duyệt */
    @Column(name = "pending_delete", nullable = false)
    @Builder.Default
    private Boolean pendingDelete = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    /** Soft-delete — set khi admin duyệt xóa chương, thay vì xóa cứng, để giữ tài liệu cho rollback. */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    @OneToMany(mappedBy = "chapter", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderIndex ASC")
    @Builder.Default
    private List<Lesson> lessons = new ArrayList<>();
}
