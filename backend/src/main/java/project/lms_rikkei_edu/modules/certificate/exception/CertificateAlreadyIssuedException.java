package project.lms_rikkei_edu.modules.certificate.exception;

public class CertificateAlreadyIssuedException extends RuntimeException {
    public CertificateAlreadyIssuedException(String message) {
        super(message);
    }
}
