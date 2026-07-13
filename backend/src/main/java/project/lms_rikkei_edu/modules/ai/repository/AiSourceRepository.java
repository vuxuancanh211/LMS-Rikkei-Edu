package project.lms_rikkei_edu.modules.ai.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import project.lms_rikkei_edu.modules.ai.entity.AiSource;
import project.lms_rikkei_edu.modules.ai.entity.enums.IngestStatus;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AiSourceRepository extends JpaRepository<AiSource, UUID> {

    List<AiSource> findByCourseIdAndDeletedAtIsNull(UUID courseId);

    /** Every active source in the system — every course's docs plus system-wide (courseId=null) docs. */
    List<AiSource> findByDeletedAtIsNull();

    /** Active sources across a set of courses — used to list all docs an instructor's courses have. */
    List<AiSource> findByCourseIdInAndDeletedAtIsNull(Collection<UUID> courseIds);

    List<AiSource> findByCourseIdAndIngestStatusAndDeletedAtIsNull(UUID courseId, IngestStatus ingestStatus);

    List<AiSource> findByLessonIdAndDeletedAtIsNull(UUID lessonId);

    List<AiSource> findByResourceIdAndDeletedAtIsNull(UUID resourceId);

    /* Không lọc deletedAt — dùng khi cần xóa HẲN toàn bộ nguồn AI (kể cả đã soft-delete trước
       đó) gắn với 1 lesson sắp bị xóa hẳn, để giải phóng FK ai_sources.lesson_id -> lessons. */
    List<AiSource> findByLessonIdIn(Collection<UUID> lessonIds);

    /* AiSource tạo từ luồng "đưa resource có sẵn vào AI" (upsertResourceSource) chỉ set
       resourceId, KHÔNG set lessonId — nên phải tìm thêm theo resourceId mới bắt hết được các
       nguồn AI gắn với những resource thuộc lesson sắp xóa, nếu không sẽ vẫn vỡ FK
       ai_sources.resource_id -> lesson_resources khi lesson_resources cascade-xóa theo lesson. */
    List<AiSource> findByResourceIdIn(Collection<UUID> resourceIds);

    /* deleteAllById() mặc định của Spring Data là entity-based (find rồi remove()) — entity vừa
       tìm được ở findByLessonIdIn/findByResourceIdIn có thể đã nằm sẵn trong persistence context,
       khiến remove() không đăng ký được thao tác xóa một cách đáng tin cậy. Dùng bulk JPQL @Modifying
       để đảm bảo LUÔN có 1 câu DELETE thật sự chạy ngay, giống documentChunkRepo/aiIngestionJobRepo. */
    @Modifying
    @Query("DELETE FROM AiSource s WHERE s.id IN :ids")
    void deleteAllByIdInBulk(@Param("ids") List<UUID> ids);
}
