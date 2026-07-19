package project.lms_rikkei_edu.modules.assignment.mapper;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import project.lms_rikkei_edu.modules.assignment.dto.response.AssignmentAttachmentResponse;
import project.lms_rikkei_edu.modules.assignment.dto.response.AssignmentResponse;
import project.lms_rikkei_edu.modules.assignment.entity.AssignmentAttachmentEntity;
import project.lms_rikkei_edu.modules.assignment.entity.AssignmentEntity;
import project.lms_rikkei_edu.modules.assignment.enums.AssignmentScope;
import project.lms_rikkei_edu.modules.assignment.enums.AssignmentStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AssignmentMapperTest {

    private final AssignmentMapper mapper = Mappers.getMapper(AssignmentMapper.class);

    @Test
    void parseAllowedFileTypes_validJson_returnsList() {
        List<String> result = mapper.parseAllowedFileTypes("[\"image/png\",\"image/jpeg\"]");

        assertThat(result).containsExactly("image/png", "image/jpeg");
    }

    @Test
    void parseAllowedFileTypes_invalidJson_returnsEmpty() {
        List<String> result = mapper.parseAllowedFileTypes("not json");

        assertThat(result).isEmpty();
    }

    @Test
    void parseAllowedFileTypes_null_returnsEmpty() {
        List<String> result = mapper.parseAllowedFileTypes(null);

        assertThat(result).isEmpty();
    }

    @Test
    void parseAllowedFileTypes_blank_returnsEmpty() {
        List<String> result = mapper.parseAllowedFileTypes("   ");

        assertThat(result).isEmpty();
    }

    @Test
    void toResponse_mapsFields() {
        var entity = assignmentEntity();

        AssignmentResponse result = mapper.toResponse(entity);

        assertThat(result.getId()).isEqualTo(entity.getId());
        assertThat(result.getCourseId()).isEqualTo(entity.getCourseId());
        assertThat(result.getTitle()).isEqualTo("Test Assignment");
        assertThat(result.getDescription()).isEqualTo("Description");
        assertThat(result.getStatus()).isEqualTo(AssignmentStatus.DRAFT);
        assertThat(result.getScope()).isEqualTo(AssignmentScope.ALL_GROUPS);
        assertThat(result.getMaxScore()).isEqualByComparingTo(BigDecimal.TEN);
        assertThat(result.getAllowedFileTypes()).containsExactly("image/png", "image/jpeg");
    }

    @Test
    void toAttachmentResponse_mapsFields() {
        var entity = attachmentEntity();

        AssignmentAttachmentResponse result = mapper.toAttachmentResponse(entity);

        assertThat(result.getId()).isEqualTo(entity.getId());
        assertThat(result.getAssignmentId()).isEqualTo(entity.getAssignmentId());
        assertThat(result.getOriginalFilename()).isEqualTo("file.pdf");
        assertThat(result.getFileSizeBytes()).isEqualTo(2048L);
        assertThat(result.getMimeType()).isEqualTo("application/pdf");
        assertThat(result.getOrderIndex()).isEqualTo(0);
    }

    private AssignmentEntity assignmentEntity() {
        var e = new AssignmentEntity();
        e.setId(UUID.randomUUID());
        e.setCourseId(UUID.randomUUID());
        e.setTitle("Test Assignment");
        e.setDescription("Description");
        e.setStatus(AssignmentStatus.DRAFT);
        e.setScope(AssignmentScope.ALL_GROUPS);
        e.setMaxScore(BigDecimal.TEN);
        e.setAllowedFileTypes("[\"image/png\",\"image/jpeg\"]");
        e.setCreatedAt(OffsetDateTime.now());
        e.setUpdatedAt(OffsetDateTime.now());
        return e;
    }

    private AssignmentAttachmentEntity attachmentEntity() {
        var e = new AssignmentAttachmentEntity();
        e.setId(UUID.randomUUID());
        e.setAssignmentId(UUID.randomUUID());
        e.setOriginalFilename("file.pdf");
        e.setFileSizeBytes(2048L);
        e.setMimeType("application/pdf");
        e.setOrderIndex(0);
        e.setUploadedAt(OffsetDateTime.now());
        return e;
    }
}
