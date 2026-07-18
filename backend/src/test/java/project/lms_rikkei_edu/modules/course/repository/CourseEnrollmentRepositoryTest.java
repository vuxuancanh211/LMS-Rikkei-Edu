package project.lms_rikkei_edu.modules.course.repository;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class CourseEnrollmentRepositoryTest {

    @Test
    void testCountMapByCourseIds() {
        // Arrange
        CourseEnrollmentRepository repository = mock(CourseEnrollmentRepository.class);
        
        UUID courseId1 = UUID.randomUUID();
        UUID courseId2 = UUID.randomUUID();
        
        List<Object[]> mockedResult = List.of(
                new Object[]{courseId1, 10L},
                new Object[]{courseId2, 5} // Mixed number types to ensure (Number) cast works
        );
        
        when(repository.countGroupedByCourseIds(anyList())).thenReturn(mockedResult);
        when(repository.countMapByCourseIds(anyList())).thenCallRealMethod();
        
        // Act
        Map<UUID, Integer> result = repository.countMapByCourseIds(List.of(courseId1, courseId2));
        
        // Assert
        assertEquals(2, result.size());
        assertEquals(10, result.get(courseId1));
        assertEquals(5, result.get(courseId2));
        
        verify(repository).countGroupedByCourseIds(List.of(courseId1, courseId2));
    }
}
