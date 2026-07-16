package project.lms_rikkei_edu.modules.forum.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import project.lms_rikkei_edu.modules.forum.entity.ForumReportEntity;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ForumReportRepository extends JpaRepository<ForumReportEntity, UUID> {

    Optional<ForumReportEntity> findByTargetTypeAndTargetIdAndReporterId(String targetType, UUID targetId, UUID reporterId);

    @Query("""
            select r from ForumReportEntity r
            where (:status is null or r.status = :status)
              and (:targetType is null or r.targetType = :targetType)
            order by case when r.status = 'PENDING' then 0 else 1 end, r.createdAt desc
            """)
    Page<ForumReportEntity> findAdminReports(@Param("status") String status, @Param("targetType") String targetType, Pageable pageable);

    @Query("""
            select r.targetId as targetId,
                   count(r.id) as totalCount,
                   sum(case when r.status = 'PENDING' then 1 else 0 end) as pendingCount
            from ForumReportEntity r
            where r.targetType = :targetType and r.targetId in :targetIds
            group by r.targetId
            """)
    List<ForumReportCountProjection> countReportsByTargets(
            @Param("targetType") String targetType,
            @Param("targetIds") Collection<UUID> targetIds);

    boolean existsByTargetTypeAndTargetIdAndStatus(String targetType, UUID targetId, String status);
}
