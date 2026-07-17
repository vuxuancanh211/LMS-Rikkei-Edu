package project.lms_rikkei_edu.modules.group.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import project.lms_rikkei_edu.modules.group.entity.StudyGroupEntity;

import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudyGroupRepository extends JpaRepository<StudyGroupEntity, UUID>, JpaSpecificationExecutor<StudyGroupEntity> {

    @Query(value = """
            SELECT sg FROM StudyGroupEntity sg
            JOIN FETCH sg.course c
            WHERE sg.instructor.id = :instructorId
            AND (:courseId IS NULL OR c.id = :courseId)
            AND (:keyword IS NULL OR LOWER(sg.name) LIKE LOWER(CONCAT('%', CAST(:keyword AS text), '%')))
            ORDER BY sg.createdAt DESC
            """,
            countQuery = """
            SELECT COUNT(sg) FROM StudyGroupEntity sg
            JOIN sg.course c
            WHERE sg.instructor.id = :instructorId
            AND (:courseId IS NULL OR c.id = :courseId)
            AND (:keyword IS NULL OR LOWER(sg.name) LIKE LOWER(CONCAT('%', CAST(:keyword AS text), '%')))
            """)
    Page<StudyGroupEntity> findByFilters(
            @Param("instructorId") UUID instructorId,
            @Param("courseId") UUID courseId,
            @Param("keyword") String keyword,
            Pageable pageable);

    @Query("SELECT sg FROM StudyGroupEntity sg JOIN FETCH sg.course WHERE sg.id = :id AND sg.instructor.id = :instructorId")
    Optional<StudyGroupEntity> findByIdAndInstructorId(@Param("id") UUID id, @Param("instructorId") UUID instructorId);

    @Query("SELECT sg FROM StudyGroupEntity sg JOIN FETCH sg.course WHERE sg.id = :id")
    Optional<StudyGroupEntity> findByIdWithCourse(@Param("id") UUID id);

    @Query("SELECT sg FROM StudyGroupEntity sg JOIN FETCH sg.course WHERE sg.id IN (SELECT gm.group.id FROM GroupMemberEntity gm WHERE gm.student.id = :studentId) ORDER BY sg.createdAt DESC")
    List<StudyGroupEntity> findByStudentId(@Param("studentId") UUID studentId);

    @Query("SELECT sg FROM StudyGroupEntity sg JOIN FETCH sg.course WHERE sg.startDate = :date")
    List<StudyGroupEntity> findByStartDateWithCourse(@Param("date") LocalDate date);

    @Query("SELECT sg FROM StudyGroupEntity sg JOIN FETCH sg.course WHERE sg.endDate = :date")
    List<StudyGroupEntity> findByEndDateWithCourse(@Param("date") LocalDate date);

    @Query("SELECT sg FROM StudyGroupEntity sg JOIN FETCH sg.course WHERE sg.endDate < :date")
    List<StudyGroupEntity> findByEndDateBeforeWithCourse(@Param("date") LocalDate date);
}
