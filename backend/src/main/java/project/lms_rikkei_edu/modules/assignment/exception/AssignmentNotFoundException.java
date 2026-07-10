package project.lms_rikkei_edu.modules.assignment.exception;

import project.lms_rikkei_edu.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class AssignmentNotFoundException extends BusinessException {

    public AssignmentNotFoundException() {
        super("Không tìm thấy bài tập", HttpStatus.NOT_FOUND);
    }
}
