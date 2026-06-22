package project.lms_rikkei_edu;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.fail;

class CiPipelineCheckTest {

    @Test
    void intentionalFailure() {
        fail("Test CI failed");
    }
}