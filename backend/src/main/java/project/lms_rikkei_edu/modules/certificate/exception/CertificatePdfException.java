package project.lms_rikkei_edu.modules.certificate.exception;

public class CertificatePdfException extends RuntimeException {
    public CertificatePdfException(String message) {
        super(message);
    }

    public CertificatePdfException(String message, Throwable cause) {
        super(message, cause);
    }
}
