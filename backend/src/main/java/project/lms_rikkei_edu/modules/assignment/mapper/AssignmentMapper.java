package project.lms_rikkei_edu.modules.assignment.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import project.lms_rikkei_edu.modules.assignment.dto.response.AssignmentAttachmentResponse;
import project.lms_rikkei_edu.modules.assignment.dto.response.AssignmentResponse;
import project.lms_rikkei_edu.modules.assignment.entity.AssignmentAttachmentEntity;
import project.lms_rikkei_edu.modules.assignment.entity.AssignmentEntity;

import java.util.Collections;
import java.util.List;

@Mapper(componentModel = "spring")
public interface AssignmentMapper {

    ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Mapping(target = "attachmentCount", ignore = true)
    @Mapping(target = "allowedFileTypes", expression = "java(parseAllowedFileTypes(entity.getAllowedFileTypes()))")
    AssignmentResponse toResponse(AssignmentEntity entity);

    AssignmentAttachmentResponse toAttachmentResponse(AssignmentAttachmentEntity entity);

    default List<String> parseAllowedFileTypes(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return OBJECT_MAPPER.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
