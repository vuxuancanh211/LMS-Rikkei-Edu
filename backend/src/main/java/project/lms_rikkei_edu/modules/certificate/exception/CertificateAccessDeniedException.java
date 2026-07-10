package project.lms_rikkei_edu.modules.certificate.exception;

public class CertificateAccessDeniedException extends RuntimeException {
    public CertificateAccessDeniedException(String message) {
        super(message);
    }
}
