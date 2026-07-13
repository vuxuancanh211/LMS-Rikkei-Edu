package project.lms_rikkei_edu.modules.group.service;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import project.lms_rikkei_edu.modules.group.dto.request.AddGroupMembersRequest;
import project.lms_rikkei_edu.modules.group.dto.request.CreateGroupRequest;
import project.lms_rikkei_edu.modules.group.dto.request.UpdateGroupRequest;
import project.lms_rikkei_edu.modules.group.dto.response.GroupDetailResponse;
import project.lms_rikkei_edu.modules.group.dto.response.GroupMemberResponse;
import project.lms_rikkei_edu.modules.group.dto.response.GroupResponse;
import project.lms_rikkei_edu.modules.group.dto.response.StudentSearchResponse;

import java.util.List;
import java.util.UUID;

public interface GroupService {

    Page<GroupResponse> getGroups(UUID courseId, String keyword, Pageable pageable);

    GroupDetailResponse getGroupDetail(UUID groupId);

    GroupResponse createGroup(@Valid CreateGroupRequest request);

    GroupResponse updateGroup(UUID groupId, @Valid UpdateGroupRequest request);

    void deleteGroup(UUID groupId);

    List<GroupMemberResponse> addMembers(UUID groupId, @Valid AddGroupMembersRequest request);

    void removeMember(UUID groupId, UUID studentId);

    List<StudentSearchResponse> searchStudentsByEmail(String email);

    List<StudentSearchResponse> getUnassignedStudents(UUID courseId);

    GroupDetailResponse getStudentGroupDetail(UUID groupId);

    List<GroupResponse> getStudentGroups();
}
