package project.lms_rikkei_edu.modules.csvimport.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import project.lms_rikkei_edu.common.constants.RedisKeyConstants;
import project.lms_rikkei_edu.common.exception.BusinessException;
import project.lms_rikkei_edu.common.security.CurrentUserProvider;
import project.lms_rikkei_edu.common.security.UserPrincipal;
import project.lms_rikkei_edu.infrastructure.redis.RedisService;
import project.lms_rikkei_edu.modules.csvimport.dto.response.GroupMemberCsvImportConfirmResponse;
import project.lms_rikkei_edu.modules.csvimport.dto.response.GroupMemberCsvImportPreviewResponse;
import project.lms_rikkei_edu.modules.group.dto.request.AddGroupMembersRequest;
import project.lms_rikkei_edu.modules.group.entity.StudyGroupEntity;
import project.lms_rikkei_edu.modules.group.repository.GroupMemberRepository;
import project.lms_rikkei_edu.modules.group.repository.StudyGroupRepository;
import project.lms_rikkei_edu.modules.group.service.GroupService;
import project.lms_rikkei_edu.modules.user.entity.UserEntity;
import project.lms_rikkei_edu.modules.user.enums.UserRole;
import project.lms_rikkei_edu.modules.user.enums.UserStatus;
import project.lms_rikkei_edu.modules.user.repository.UserRepository;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupMemberCsvImportServiceImplTest {

    @Mock
    private StudyGroupRepository studyGroupRepository;

    @Mock
    private GroupMemberRepository groupMemberRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private GroupService groupService;

    @Mock
    private RedisService redisService;

    @Mock
    private CurrentUserProvider currentUserProvider;

    private GroupMemberCsvImportServiceImpl service;
    private final UUID groupId = UUID.randomUUID();
    private final UUID instructorId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new GroupMemberCsvImportServiceImpl(
                studyGroupRepository,
                groupMemberRepository,
                userRepository,
                groupService,
                redisService,
                currentUserProvider);
        ReflectionTestUtils.setField(service, "maxCsvRows", 500);
    }

    @Test
    void preview_returnsValidAndInvalidRows() {
        UserEntity currentUser = user(instructorId, "teacher@test.com", UserRole.INSTRUCTOR);
        UserEntity student = user(UUID.randomUUID(), "student@test.com", UserRole.STUDENT);
        when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(new UserPrincipal(currentUser)));
        when(studyGroupRepository.findByIdAndInstructorId(groupId, instructorId)).thenReturn(Optional.of(group(3)));
        when(userRepository.findByEmailIgnoreCaseInAndDeletedAtIsNull(anyList())).thenReturn(List.of(student));
        when(groupMemberRepository.findExistingStudentIds(eq(groupId), anyList())).thenReturn(List.of());
        when(groupMemberRepository.countByGroupId(groupId)).thenReturn(0L);

        GroupMemberCsvImportPreviewResponse result = service.preview(groupId, csv("email\nstudent@test.com\nmissing@test.com\nstudent@test.com\nbad-email"));

        assertThat(result.getTotalRows()).isEqualTo(4);
        assertThat(result.getValidCount()).isEqualTo(1);
        assertThat(result.getNotFoundCount()).isEqualTo(1);
        assertThat(result.getDuplicateInFileCount()).isEqualTo(1);
        assertThat(result.getFormatErrorCount()).isEqualTo(1);
        assertThat(result.getRows()).extracting("status")
                .containsExactly("VALID", "NOT_FOUND", "DUPLICATE_IN_FILE", "FORMAT_ERROR");
        assertThat(result.getToken()).isNotBlank();
        verify(redisService).set(anyString(), anyString(), anyLong());
    }

    @Test
    void preview_marksRowsExceedingCapacity() {
        UserEntity currentUser = user(instructorId, "teacher@test.com", UserRole.INSTRUCTOR);
        UserEntity first = user(UUID.randomUUID(), "first@test.com", UserRole.STUDENT);
        UserEntity second = user(UUID.randomUUID(), "second@test.com", UserRole.STUDENT);
        when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(new UserPrincipal(currentUser)));
        when(studyGroupRepository.findByIdAndInstructorId(groupId, instructorId)).thenReturn(Optional.of(group(1)));
        when(userRepository.findByEmailIgnoreCaseInAndDeletedAtIsNull(anyList())).thenReturn(List.of(first, second));
        when(groupMemberRepository.findExistingStudentIds(eq(groupId), anyList())).thenReturn(List.of());
        when(groupMemberRepository.countByGroupId(groupId)).thenReturn(0L);

        GroupMemberCsvImportPreviewResponse result = service.preview(groupId, csv("email\nfirst@test.com\nsecond@test.com"));

        assertThat(result.getValidCount()).isEqualTo(1);
        assertThat(result.getCapacityExceededCount()).isEqualTo(1);
        assertThat(result.getRows()).extracting("status")
                .containsExactly("VALID", "CAPACITY_EXCEEDED");
    }

    @Test
    void confirm_addsOnlyPreviewValidEmailsAndDeletesToken() {
        String token = "preview-token";
        String redisValue = "{\"groupId\":\"" + groupId + "\",\"validEmails\":[\"student@test.com\"],\"rows\":[{\"rowNumber\":2,\"email\":\"student@test.com\",\"status\":\"VALID\",\"errors\":[]}]}";
        when(redisService.get(RedisKeyConstants.GROUP_MEMBER_CSV_IMPORT_PREVIEW + token)).thenReturn(Optional.of(redisValue));

        GroupMemberCsvImportConfirmResponse result = service.confirm(groupId, token);

        assertThat(result.getSuccessCount()).isEqualTo(1);
        assertThat(result.getFailCount()).isZero();
        assertThat(result.getResults().getFirst().getStatus()).isEqualTo("IMPORTED");
        ArgumentCaptor<AddGroupMembersRequest> requestCaptor = ArgumentCaptor.forClass(AddGroupMembersRequest.class);
        verify(groupService).addMembers(eq(groupId), requestCaptor.capture());
        assertThat(requestCaptor.getValue().getEmails()).containsExactly("student@test.com");
        verify(redisService).delete(RedisKeyConstants.GROUP_MEMBER_CSV_IMPORT_PREVIEW + token);
    }

    @Test
    void confirm_rejectsTokenForDifferentGroup() {
        String token = "preview-token";
        String redisValue = "{\"groupId\":\"" + UUID.randomUUID() + "\",\"validEmails\":[\"student@test.com\"],\"rows\":[]}";
        when(redisService.get(RedisKeyConstants.GROUP_MEMBER_CSV_IMPORT_PREVIEW + token)).thenReturn(Optional.of(redisValue));

        assertThatThrownBy(() -> service.confirm(groupId, token))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("does not match");
    }

    private StudyGroupEntity group(Integer maxCapacity) {
        StudyGroupEntity group = new StudyGroupEntity();
        group.setId(groupId);
        group.setMaxCapacity(maxCapacity);
        return group;
    }

    private UserEntity user(UUID id, String email, UserRole role) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setEmail(email);
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }

    private MockMultipartFile csv(String content) {
        return new MockMultipartFile(
                "file",
                "students.csv",
                MediaType.TEXT_PLAIN_VALUE,
                content.getBytes(StandardCharsets.UTF_8));
    }
}
