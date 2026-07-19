package project.lms_rikkei_edu.modules.certificate.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import project.lms_rikkei_edu.modules.certificate.dto.response.CertificateResponse;
import project.lms_rikkei_edu.modules.certificate.dto.response.CertificateVerifyResponse;
import project.lms_rikkei_edu.modules.certificate.entity.CertificateEntity;

@Mapper(componentModel = "spring")
public interface CertificateMapper {

    @Mapping(target = "studentName", source = "entity.student.fullName")
    @Mapping(target = "courseTitle", source = "entity.course.title")
    @Mapping(target = "courseThumbnailUrl", source = "entity.course.thumbnailUrl")
    @Mapping(target = "instructorName", source = "instructorName")
    CertificateResponse toResponse(CertificateEntity entity, String instructorName);

    @Mapping(target = "studentName", source = "entity.student.fullName")
    @Mapping(target = "courseTitle", source = "entity.course.title")
    @Mapping(target = "courseThumbnailUrl", source = "entity.course.thumbnailUrl")
    @Mapping(target = "instructorName", source = "instructorName")
    CertificateVerifyResponse toVerifyResponse(CertificateEntity entity, String instructorName);
}
