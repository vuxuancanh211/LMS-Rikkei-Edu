package project.lms_rikkei_edu.modules.ai.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import project.lms_rikkei_edu.modules.ai.entity.DocumentChunk;

import java.util.List;
import java.util.UUID;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, UUID> {

    List<DocumentChunk> findBySourceIdOrderByChunkIndex(UUID sourceId);

    int countBySourceId(UUID sourceId);

    @Modifying
    @Query("DELETE FROM DocumentChunk c WHERE c.sourceId = :sourceId")
    void deleteBySourceId(@Param("sourceId") UUID sourceId);

    @Modifying
    @Query("DELETE FROM DocumentChunk c WHERE c.sourceId IN :sourceIds")
    void deleteBySourceIdIn(@Param("sourceIds") List<UUID> sourceIds);
}
