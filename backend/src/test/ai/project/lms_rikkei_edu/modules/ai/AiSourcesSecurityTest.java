package project.lms_rikkei_edu.modules.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import project.lms_rikkei_edu.common.security.CurrentUserProvider;
import project.lms_rikkei_edu.common.security.CustomUserDetailsService;
import project.lms_rikkei_edu.common.security.JwtAuthenticationFilter;
import project.lms_rikkei_edu.common.security.JwtService;
import project.lms_rikkei_edu.common.security.UserPrincipal;
import project.lms_rikkei_edu.config.SecurityConfig;
import project.lms_rikkei_edu.infrastructure.redis.RedisService;
import project.lms_rikkei_edu.infrastructure.s3.S3Service;
import project.lms_rikkei_edu.modules.ai.controller.AiSourceController;
import project.lms_rikkei_edu.modules.ai.service.AiSourceService;
import project.lms_rikkei_edu.modules.course.repository.CourseRepository;
import project.lms_rikkei_edu.modules.user.enums.UserRole;
import project.lms_rikkei_edu.modules.user.repository.UserRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Loads the real {@link SecurityConfig} filter chain (unlike the standalone-MockMvc
 * tests elsewhere in this package) to prove the role restriction on
 * {@code /api/ai/sources/**} actually takes effect, not just that the controller
 * behaves correctly when Spring Security is bypassed.
 *
 * <p>MockMvc is built manually with {@code .apply(springSecurity())} rather than
 * relying on the {@code @WebMvcTest}-injected instance, because this Spring Boot
 * version's web-mvc test slice does not automatically wire the Spring Security test
 * support that makes {@code @WithMockUser} take effect.
 */
@WebMvcTest(controllers = AiSourceController.class)
@EnableWebSecurity
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, CustomUserDetailsService.class})
class AiSourcesSecurityTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @MockitoBean private AiSourceService sourceService;
    @MockitoBean private CurrentUserProvider currentUserProvider;
    @MockitoBean private CourseRepository courseRepository;
    @MockitoBean private S3Service s3Service;
    @MockitoBean private JwtService jwtService;
    @MockitoBean private RedisService redisService;
    @MockitoBean private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @Nested
    class RoleEnforcement {

        @Test
        void anonymousRequestIsUnauthorized() throws Exception {
            mockMvc.perform(get("/api/ai/sources").param("courseId", UUID.randomUUID().toString()))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockUser(roles = "STUDENT")
        void studentIsForbidden() throws Exception {
            mockMvc.perform(get("/api/ai/sources").param("courseId", UUID.randomUUID().toString()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = "INSTRUCTOR")
        void instructorOwningTheCourseIsAllowedThrough() throws Exception {
            // Asserting only that the role gate did not reject the request (401/403 are
            // both returned by Spring Security before the controller ever runs), plus
            // that the service was actually invoked — not the exact response status —
            // to stay independent of unrelated response-serialization concerns.
            UUID courseId = UUID.randomUUID();
            UserPrincipal instructor = mock(UserPrincipal.class);
            when(instructor.getId()).thenReturn(UUID.randomUUID());
            when(instructor.getRole()).thenReturn(UserRole.INSTRUCTOR);
            when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(instructor));
            when(courseRepository.existsByIdAndInstructorId(any(), any())).thenReturn(true);
            when(sourceService.listByCourse(any())).thenReturn(List.of());

            mockMvc.perform(get("/api/ai/sources").param("courseId", courseId.toString()))
                    .andExpect(result -> {
                        int status = result.getResponse().getStatus();
                        org.assertj.core.api.Assertions.assertThat(status)
                                .as("role gate must let an INSTRUCTOR through (401/403 mean it didn't)")
                                .isNotIn(401, 403);
                    });

            verify(sourceService).listByCourse(courseId);
        }
    }
}
