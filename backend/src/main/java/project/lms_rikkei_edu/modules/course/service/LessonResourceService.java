package project.lms_rikkei_edu.modules.course.service;

import project.lms_rikkei_edu.modules.course.dto.request.ResourceConfirmUploadRequest;
import project.lms_rikkei_edu.modules.course.dto.request.ResourceUploadPresignRequest;
import project.lms_rikkei_edu.modules.course.dto.response.LessonResourceResponse;
import project.lms_rikkei_edu.modules.course.dto.response.ResourceDownloadUrlResponse;
import project.lms_rikkei_edu.modules.course.dto.response.ResourceUploadPresignResponse;

import java.util.List;
import java.util.UUID;

public interface LessonResourceService {

    /** Bước 1: sinh presigned PUT URL để client upload thẳng lên S3 */
    ResourceUploadPresignResponse requestUploadUrl(UUID instructorId, UUID courseId, UUID lessonId,
                                                   ResourceUploadPresignRequest request);

    /** Bước 2: sau khi upload xong, xác nhận và lưu record vào DB */
    LessonResourceResponse confirmUpload(UUID instructorId, UUID courseId, UUID lessonId,
                                         ResourceConfirmUploadRequest request);

    /** Lấy presigned GET URL để download file */
    ResourceDownloadUrlResponse getDownloadUrl(UUID instructorId, UUID courseId, UUID lessonId, UUID resourceId);

    /** Danh sách resource của lesson */
    List<LessonResourceResponse> listResources(UUID instructorId, UUID courseId, UUID lessonId);

    /** Xóa resource (xóa mềm + xóa trên S3) */
    void deleteResource(UUID instructorId, UUID courseId, UUID lessonId, UUID resourceId);
}
