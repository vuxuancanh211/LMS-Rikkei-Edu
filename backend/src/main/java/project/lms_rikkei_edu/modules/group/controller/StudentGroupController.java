package project.lms_rikkei_edu.modules.group.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import project.lms_rikkei_edu.modules.group.dto.response.GroupDetailResponse;
import project.lms_rikkei_edu.modules.group.dto.response.GroupResponse;
import project.lms_rikkei_edu.modules.group.service.GroupService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/student/groups")
@RequiredArgsConstructor
@PreAuthorize("hasRole('STUDENT')")
public class StudentGroupController {

    private final GroupService groupService;

    @GetMapping
    public ResponseEntity<List<GroupResponse>> getGroups() {
        return ResponseEntity.ok(groupService.getStudentGroups());
    }

    @GetMapping("/{groupId}")
    public ResponseEntity<GroupDetailResponse> getGroupDetail(@PathVariable UUID groupId) {
        return ResponseEntity.ok(groupService.getStudentGroupDetail(groupId));
    }
}
