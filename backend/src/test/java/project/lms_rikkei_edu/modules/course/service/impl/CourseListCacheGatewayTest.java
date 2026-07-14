package project.lms_rikkei_edu.modules.course.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import project.lms_rikkei_edu.modules.course.dto.response.CourseResponse;
import project.lms_rikkei_edu.modules.course.entity.Course;
import project.lms_rikkei_edu.modules.course.mapper.CourseMapper;
import project.lms_rikkei_edu.modules.course.repository.CourseRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CourseListCacheGatewayTest {

    @Mock private CourseRepository courseRepository;
    @Mock private CourseMapper courseMapper;

    private CourseListCacheGateway gateway;
    private UUID instructorId;

    @BeforeEach
    void setUp() {
        gateway = new CourseListCacheGateway(courseRepository, courseMapper);
        instructorId = UUID.randomUUID();
        when(courseMapper.toResponse(any())).thenAnswer(inv -> {
            Course c = inv.getArgument(0);
            return CourseResponse.builder().id(c.getId()).title(c.getTitle()).build();
        });
    }

    private Course course(String title, String description, Instant createdAt) {
        Course c = new Course();
        c.setId(UUID.randomUUID());
        c.setInstructorId(instructorId);
        c.setTitle(title);
        c.setDescription(description);
        c.setCreatedAt(createdAt);
        return c;
    }

    @Test
    void find_nullKeyword_delegatesToFindAllByInstructorId() {
        Pageable pageable = PageRequest.of(0, 10);
        Course c = course("Java Boot", "desc", Instant.now());
        when(courseRepository.findAllByInstructorId(instructorId, pageable))
                .thenReturn(new PageImpl<>(List.of(c), pageable, 1));

        CourseListCacheGateway.Entry result = gateway.find(instructorId, pageable, null);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getTotalElements()).isEqualTo(1);
        verify(courseRepository, never()).findAll(any(org.springframework.data.jpa.domain.Specification.class));
    }

    @Test
    void find_blankKeyword_delegatesToFindAllByInstructorId() {
        Pageable pageable = PageRequest.of(0, 10);
        when(courseRepository.findAllByInstructorId(instructorId, pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        CourseListCacheGateway.Entry result = gateway.find(instructorId, pageable, "   ");

        assertThat(result.getContent()).isEmpty();
        verify(courseRepository).findAllByInstructorId(instructorId, pageable);
    }

    @Test
    void find_withKeyword_searchesViaSpecificationInsteadOfPlainList() {
        Pageable pageable = PageRequest.of(0, 10);
        Course c = course("PostgreSQL nâng cao", "học query", Instant.now());
        when(courseRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
                .thenReturn(List.of(c));

        CourseListCacheGateway.Entry result = gateway.find(instructorId, pageable, "postgresql");

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getTotalElements()).isEqualTo(1);
        verify(courseRepository, never()).findAllByInstructorId(any(), any());
    }

    @Test
    void find_multiWordKeyword_ranksByNumberOfMatchingTokens() {
        Pageable pageable = PageRequest.of(0, 10);
        Instant now = Instant.now();
        // "java" matches only 1 token, "java spring boot" matches all 3 tokens from the keyword
        Course oneMatch = course("Java Basics", "intro", now);
        Course threeMatches = course("Java Spring Boot", "spring boot framework", now);
        when(courseRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
                .thenReturn(List.of(oneMatch, threeMatches));

        CourseListCacheGateway.Entry result = gateway.find(instructorId, pageable, "java spring boot");

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).getId()).isEqualTo(threeMatches.getId());
        assertThat(result.getContent().get(1).getId()).isEqualTo(oneMatch.getId());
    }

    @Test
    void find_tiedMatchScore_ordersByCreatedAtDescending() {
        Pageable pageable = PageRequest.of(0, 10);
        Instant older = Instant.now().minusSeconds(3600);
        Instant newer = Instant.now();
        Course olderCourse = course("Java Basics", null, older);
        Course newerCourse = course("Java Advanced", null, newer);
        when(courseRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
                .thenReturn(List.of(olderCourse, newerCourse));

        CourseListCacheGateway.Entry result = gateway.find(instructorId, pageable, "java");

        assertThat(result.getContent().get(0).getId()).isEqualTo(newerCourse.getId());
        assertThat(result.getContent().get(1).getId()).isEqualTo(olderCourse.getId());
    }

    @Test
    void find_nullDescription_doesNotThrowDuringMatchScoring() {
        Pageable pageable = PageRequest.of(0, 10);
        Course c = course("Docker Basics", null, Instant.now());
        when(courseRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
                .thenReturn(List.of(c));

        CourseListCacheGateway.Entry result = gateway.find(instructorId, pageable, "docker");

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void find_pageOffsetBeyondResults_returnsEmptyContentWithCorrectTotal() {
        Pageable pageable = PageRequest.of(5, 10); // offset 50, well beyond the 1 matched result
        Course c = course("Java Basics", "intro", Instant.now());
        when(courseRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
                .thenReturn(List.of(c));

        CourseListCacheGateway.Entry result = gateway.find(instructorId, pageable, "java");

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void find_pageSizeSmallerThanResults_returnsCorrectSlice() {
        Pageable pageable = PageRequest.of(0, 1);
        Instant now = Instant.now();
        Course c1 = course("Java Basics", "intro java", now);
        Course c2 = course("Java Advanced", "advanced java", now.minusSeconds(10));
        when(courseRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
                .thenReturn(List.of(c1, c2));

        CourseListCacheGateway.Entry result = gateway.find(instructorId, pageable, "java");

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getTotalElements()).isEqualTo(2);
    }
}
