package project.lms_rikkei_edu.modules.group.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import project.lms_rikkei_edu.common.exception.BusinessException;
import project.lms_rikkei_edu.common.exception.GlobalExceptionHandler;
import project.lms_rikkei_edu.modules.group.dto.request.AddGroupMembersRequest;
import project.lms_rikkei_edu.modules.group.dto.request.CreateGroupRequest;
import project.lms_rikkei_edu.modules.group.dto.request.UpdateGroupRequest;
import project.lms_rikkei_edu.modules.group.dto.response.GroupDetailResponse;
import project.lms_rikkei_edu.modules.group.dto.response.GroupMemberResponse;
import project.lms_rikkei_edu.modules.group.dto.response.GroupResponse;
import project.lms_rikkei_edu.modules.group.dto.response.StudentSearchResponse;
import project.lms_rikkei_edu.modules.group.service.GroupService;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GroupControllerTest {

    private GroupService groupService;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private final UUID groupId = UUID.randomUUID();
    private final UUID courseId = UUID.randomUUID();
    private final UUID studentId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        groupService = mock(GroupService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new GroupController(groupService))
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Nested
    class ListGroups {

        @Test
        void returns200_withPagedGroups() throws Exception {
            when(groupService.getGroups(eq(courseId), eq("React"), any()))
                    .thenReturn(new PageImpl<>(List.of(groupResponse()), PageRequest.of(0, 10), 1));

            mockMvc.perform(get("/api/instructor/groups")
                            .param("courseId", courseId.toString())
                            .param("keyword", "React"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].id").value(groupId.toString()));
        }
    }

    @Nested
    class GetGroupDetail {

        @Test
        void returns200_withGroupMembers() throws Exception {
            when(groupService.getGroupDetail(groupId)).thenReturn(groupDetailResponse());

            mockMvc.perform(get("/api/instructor/groups/{groupId}", groupId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(groupId.toString()))
                    .andExpect(jsonPath("$.members", hasSize(1)));
        }

        @Test
        void returns404_whenServiceThrowsNotFound() throws Exception {
            when(groupService.getGroupDetail(groupId))
                    .thenThrow(new BusinessException("Group not found", HttpStatus.NOT_FOUND));

            mockMvc.perform(get("/api/instructor/groups/{groupId}", groupId))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    class CreateGroup {

        @Test
        void returns201_whenPayloadValid() throws Exception {
            CreateGroupRequest request = createGroupRequest();
            when(groupService.createGroup(any())).thenReturn(groupResponse());

            mockMvc.perform(post("/api/instructor/groups")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(groupId.toString()));
        }

        @Test
        void returns400_whenEndDateMissing() throws Exception {
            CreateGroupRequest request = createGroupRequest();
            request.setEndDate(null);

            mockMvc.perform(post("/api/instructor/groups")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void returns400_whenMaxCapacityIsNotPositive() throws Exception {
            CreateGroupRequest request = createGroupRequest();
            request.setMaxCapacity(0);

            mockMvc.perform(post("/api/instructor/groups")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    class UpdateGroup {

        @Test
        void returns200_whenPayloadValid() throws Exception {
            UpdateGroupRequest request = new UpdateGroupRequest();
            request.setName("Updated Group");
            request.setDescription("Updated desc");
            request.setMaxCapacity(30);
            request.setStartDate(LocalDate.now().plusDays(1));
            request.setEndDate(LocalDate.now().plusDays(10));

            when(groupService.updateGroup(eq(groupId), any())).thenReturn(groupResponse("Updated Group"));

            mockMvc.perform(put("/api/instructor/groups/{groupId}", groupId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Updated Group"));
        }

        @Test
        void returns400_whenMaxCapacityIsNotPositive() throws Exception {
            UpdateGroupRequest request = new UpdateGroupRequest();
            request.setName("Updated Group");
            request.setMaxCapacity(0);

            mockMvc.perform(put("/api/instructor/groups/{groupId}", groupId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    class DeleteGroup {

        @Test
        void returns204_whenDeleted() throws Exception {
            mockMvc.perform(delete("/api/instructor/groups/{groupId}", groupId))
                    .andExpect(status().isNoContent());

            verify(groupService).deleteGroup(groupId);
        }
    }

    @Nested
    class SearchStudents {

        @Test
        void returns200_withSearchResults() throws Exception {
            when(groupService.searchStudentsByEmail("student@example.com"))
                    .thenReturn(List.of(studentSearchResponse()));

            mockMvc.perform(get("/api/instructor/groups/students/search")
                            .param("email", "student@example.com"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].email").value("student@example.com"));
        }
    }

    @Nested
    class AddMembers {

        @Test
        void returns201_whenMembersAdded() throws Exception {
            AddGroupMembersRequest request = new AddGroupMembersRequest();
            request.setEmails(List.of("student@example.com"));
            when(groupService.addMembers(eq(groupId), any())).thenReturn(List.of(memberResponse()));

            mockMvc.perform(post("/api/instructor/groups/{groupId}/members", groupId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].studentEmail").value("student@example.com"));
        }

        @Test
        void returns400_whenEmailListEmpty() throws Exception {
            AddGroupMembersRequest request = new AddGroupMembersRequest();
            request.setEmails(List.of());

            mockMvc.perform(post("/api/instructor/groups/{groupId}/members", groupId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    class RemoveMember {

        @Test
        void returns204_whenRemoved() throws Exception {
            mockMvc.perform(delete("/api/instructor/groups/{groupId}/members/{studentId}", groupId, studentId))
                    .andExpect(status().isNoContent());

            verify(groupService).removeMember(groupId, studentId);
        }
    }

    private CreateGroupRequest createGroupRequest() {
        CreateGroupRequest request = new CreateGroupRequest();
        request.setCourseId(courseId);
        request.setName("Group A");
        request.setDescription("Description");
        request.setMaxCapacity(20);
        request.setStartDate(LocalDate.now().plusDays(1));
        request.setEndDate(LocalDate.now().plusDays(10));
        return request;
    }

    private GroupResponse groupResponse() {
        return groupResponse("Group A");
    }

    private GroupResponse groupResponse(String name) {
        return GroupResponse.builder()
                .id(groupId)
                .courseId(courseId)
                .courseTitle("React Basics")
                .name(name)
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

    private StudentSearchResponse studentSearchResponse() {
        return StudentSearchResponse.builder()
                .id(studentId)
                .email("student@example.com")
                .fullName("Student One")
                .avatarUrl(null)
                .build();
    }
}
