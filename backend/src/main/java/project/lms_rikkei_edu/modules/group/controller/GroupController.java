package project.lms_rikkei_edu.modules.group.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import project.lms_rikkei_edu.modules.group.dto.request.AddGroupMembersRequest;
import project.lms_rikkei_edu.modules.group.dto.request.CreateGroupRequest;
import project.lms_rikkei_edu.modules.group.dto.request.UpdateGroupRequest;
import project.lms_rikkei_edu.modules.group.dto.response.GroupDetailResponse;
import project.lms_rikkei_edu.modules.group.dto.response.GroupMemberResponse;
import project.lms_rikkei_edu.modules.group.dto.response.GroupResponse;
import project.lms_rikkei_edu.modules.group.dto.response.StudentSearchResponse;
import project.lms_rikkei_edu.modules.group.service.GroupService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/instructor/groups")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
public class GroupController {

    private final GroupService groupService;

    @GetMapping
    public ResponseEntity<Page<GroupResponse>> getGroups(
            @RequestParam(required = false) UUID courseId,
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(groupService.getGroups(courseId, keyword, pageable));
    }

    @GetMapping("/{groupId}")
    public ResponseEntity<GroupDetailResponse> getGroupDetail(@PathVariable UUID groupId) {
        return ResponseEntity.ok(groupService.getGroupDetail(groupId));
    }

    @PostMapping
    public ResponseEntity<GroupResponse> createGroup(@Valid @RequestBody CreateGroupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(groupService.createGroup(request));
    }

    @PutMapping("/{groupId}")
    public ResponseEntity<GroupResponse> updateGroup(
            @PathVariable UUID groupId,
            @Valid @RequestBody UpdateGroupRequest request) {
        return ResponseEntity.ok(groupService.updateGroup(groupId, request));
    }

    @DeleteMapping("/{groupId}")
    public ResponseEntity<Void> deleteGroup(@PathVariable UUID groupId) {
        groupService.deleteGroup(groupId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/students/unassigned")
    public ResponseEntity<List<StudentSearchResponse>> getUnassignedStudents(
            @RequestParam(required = false) UUID courseId) {
        return ResponseEntity.ok(groupService.getUnassignedStudents(courseId));
    }

    @PostMapping("/{groupId}/members")
    public ResponseEntity<List<GroupMemberResponse>> addMembers(
            @PathVariable UUID groupId,
            @Valid @RequestBody AddGroupMembersRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(groupService.addMembers(groupId, request));
    }

    @DeleteMapping("/{groupId}/members/{studentId}")
    public ResponseEntity<Void> removeMember(
            @PathVariable UUID groupId,
            @PathVariable UUID studentId) {
        groupService.removeMember(groupId, studentId);
        return ResponseEntity.noContent().build();
    }
}
