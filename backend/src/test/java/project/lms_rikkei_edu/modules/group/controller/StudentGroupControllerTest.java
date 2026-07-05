package project.lms_rikkei_edu.modules.group.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import project.lms_rikkei_edu.common.exception.BusinessException;
import project.lms_rikkei_edu.common.exception.GlobalExceptionHandler;
import project.lms_rikkei_edu.modules.group.dto.response.GroupDetailResponse;
import project.lms_rikkei_edu.modules.group.dto.response.GroupMemberResponse;
import project.lms_rikkei_edu.modules.group.dto.response.GroupResponse;
import project.lms_rikkei_edu.modules.group.service.GroupService;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StudentGroupControllerTest {

    private GroupService groupService;
    private MockMvc mockMvc;

    private final UUID groupId = UUID.randomUUID();
    private final UUID courseId = UUID.randomUUID();
    private final UUID studentId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        groupService = mock(GroupService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new StudentGroupController(groupService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Nested
    class ListGroups {

        @Test
        void returns200_withStudentGroups() throws Exception {
            when(groupService.getStudentGroups()).thenReturn(List.of(groupResponse()));

            mockMvc.perform(get("/api/student/groups"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].id").value(groupId.toString()))
                    .andExpect(jsonPath("$[0].courseTitle").value("React Basics"));
        }
    }

    @Nested
    class GetGroupDetail {

        @Test
        void returns200_withGroupDetailAndMembers() throws Exception {
            when(groupService.getStudentGroupDetail(groupId)).thenReturn(groupDetailResponse());

            mockMvc.perform(get("/api/student/groups/{groupId}", groupId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(groupId.toString()))
                    .andExpect(jsonPath("$.members", hasSize(1)))
                    .andExpect(jsonPath("$.members[0].studentEmail").value("student@example.com"));
        }

        @Test
        void returns403_whenCurrentStudentIsNotGroupMember() throws Exception {
            when(groupService.getStudentGroupDetail(groupId))
                    .thenThrow(new BusinessException("You are not a member of this group", HttpStatus.FORBIDDEN));

            mockMvc.perform(get("/api/student/groups/{groupId}", groupId))
                    .andExpect(status().isForbidden());
        }
    }

    private GroupResponse groupResponse() {
        return GroupResponse.builder()
                .id(groupId)
                .courseId(courseId)
                .courseTitle("React Basics")
                .name("Group A")
                .description("Description")
                .maxCapacity(20)
                .memberCount(1)
                .startDate(LocalDate.now().plusDays(1))
                .endDate(LocalDate.now().plusDays(10))
                .status("UPCOMING")
                .createdAt(OffsetDateTime.now())
                .build();
    }

    private GroupDetailResponse groupDetailResponse() {
        return GroupDetailResponse.builder()
                .id(groupId)
                .courseId(courseId)
                .courseTitle("React Basics")
                .name("Group A")
                .description("Description")
                .maxCapacity(20)
                .memberCount(1)
                .startDate(LocalDate.now().plusDays(1))
                .endDate(LocalDate.now().plusDays(10))
                .status("UPCOMING")
                .createdAt(OffsetDateTime.now())
                .members(List.of(memberResponse()))
                .build();
    }

    private GroupMemberResponse memberResponse() {
        return GroupMemberResponse.builder()
                .id(UUID.randomUUID())
                .studentId(studentId)
                .studentName("Student One")
                .studentEmail("student@example.com")
                .avatarUrl(null)
                .joinedAt(OffsetDateTime.now())
                .build();
    }
}
