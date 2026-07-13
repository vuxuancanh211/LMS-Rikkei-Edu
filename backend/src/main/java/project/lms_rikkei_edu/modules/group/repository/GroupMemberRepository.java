package project.lms_rikkei_edu.modules.group.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import project.lms_rikkei_edu.modules.group.entity.GroupMemberEntity;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GroupMemberRepository extends JpaRepository<GroupMemberEntity, UUID> {

    @Query("SELECT gm FROM GroupMemberEntity gm JOIN FETCH gm.student WHERE gm.group.id = :groupId ORDER BY gm.joinedAt ASC")
    List<GroupMemberEntity> findByGroupIdWithStudent(@Param("groupId") UUID groupId);

    @Query("SELECT gm FROM GroupMemberEntity gm JOIN FETCH gm.student WHERE gm.group.id = :groupId AND gm.student.id = :studentId")
    Optional<GroupMemberEntity> findByGroupIdAndStudentIdWithStudent(@Param("groupId") UUID groupId, @Param("studentId") UUID studentId);

    boolean existsByGroupIdAndStudentId(UUID groupId, UUID studentId);

    @Query("SELECT gm.student.id FROM GroupMemberEntity gm WHERE gm.group.id = :groupId AND gm.student.id IN :studentIds")
    List<UUID> findExistingStudentIds(@Param("groupId") UUID groupId, @Param("studentIds") Collection<UUID> studentIds);

    long countByGroupId(UUID groupId);

    void deleteByGroupIdAndStudentId(UUID groupId, UUID studentId);

    void deleteByGroupId(UUID groupId);

    @Query("SELECT gm.student.id FROM GroupMemberEntity gm WHERE gm.group.course.id = :courseId")
    List<UUID> findStudentIdsByCourseId(@Param("courseId") UUID courseId);

    @Query("SELECT gm.group.id FROM GroupMemberEntity gm WHERE gm.student.id = :studentId AND gm.group.course.id = :courseId")
    List<UUID> findGroupIdsByStudentIdAndCourseId(@Param("studentId") UUID studentId, @Param("courseId") UUID courseId);
}
