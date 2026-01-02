package com.dev.LMS.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;

import com.dev.LMS.dto.CourseDto;
import com.dev.LMS.dto.LessonDto;
import com.dev.LMS.dto.LessonResourceDto;
import com.dev.LMS.dto.StudentDto;
import com.dev.LMS.model.*;
import com.dev.LMS.repository.CourseRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class CourseService {

    private final CourseRepository courseRepository;
    private final UserService userService;
    private final EmailService emailService;
    private final NotificationService notificationService;

    @Value("${file.upload.base-path.lesson-resources}")
    private Path resourcesPath;

    public CourseService(CourseRepository courseRepository,
                         UserService userService,
                         EmailService emailService,
                         NotificationService notificationService) {
        this.courseRepository = courseRepository;
        this.userService = userService;
        this.emailService = emailService;
        this.notificationService = notificationService;
    }

    public Course createCourse(Course course, Instructor instructor){
        course.setInstructor(instructor);
        courseRepository.save(course);
        return course;
    }

    public Course getCourse(String courseName) {
        return courseRepository.findByName(courseName).orElse(null);
    }

    public Course getCourseById(int courseId) {
        return courseRepository.findById(courseId).orElse(null);
    }

    public List<Course> getAllCourses() {
        return courseRepository.findAll();
    }

    public Set<Course> getCreatedCourses(Instructor instructor) {
        return instructor.getCreatedCourses();
    }

    public Set<Course> getEnrolledCourses(Student student) {
        return student.getEnrolled_courses();
    }

    public Lesson addLesson(Course course, Lesson lesson) {
        course.addLesson(lesson);

        Notification notificationMessage = notificationService.createNotification(
                "New Lesson Added By Instructor: " + course.getInstructor().getName()
        );

        for (Student s : course.getEnrolled_students()) {
            notificationService.addNotifcationStudent(notificationMessage, s);
            emailService.sendEmail(
                    s.getEmail(),
                    s.getName(),
                    "New Lesson",
                    "A new lesson added " + lesson.getTitle() + " in Course: " + course.getName(),
                    course.getInstructor().getName()
            );
        }

        courseRepository.save(course);

        // исправлено: берем последний элемент списка через индекс
        List<Lesson> lessons = course.getLessons();
        return lessons.get(lessons.size() - 1);
    }

    public Lesson getLessonbyId(Course course, int lessonId) {
        for (Lesson l : course.getLessons()) {
            if (l.getLesson_id() == lessonId) return l;
        }
        return null;
    }

    public String addLessonResource(Course course, int lessonId, MultipartFile file) {
        String fileName = file.getOriginalFilename();
        String fileType = file.getContentType();

        try {
            if (fileName.contains("..")) throw new RuntimeException("Invalid file name");
            Files.copy(file.getInputStream(), this.resourcesPath.resolve(fileName));
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }

        Lesson lesson = getLessonbyId(course, lessonId);
        if (lesson != null) {
            lesson.addLessonResource(new LessonResource(fileName, fileType));
            courseRepository.save(course);
            return "file added successfully: " + fileName;
        } else throw new IllegalStateException("Lesson not found");
    }

    public List<LessonResourceDto> getLessonResources(Course course, User user, int lessonId) {
        Lesson lesson = getLessonbyId(course, lessonId);
        if (lesson == null) throw new IllegalStateException("Lesson not found");

        if (user instanceof Instructor) {
            if (((Instructor) user).getId() != course.getInstructor().getId())
                throw new IllegalStateException("You are not authorized to access this resource");
        }

        if (user instanceof Student) {
            Student student = (Student) user;
            if (!student.getEnrolled_courses().contains(course) || !lesson.getAttendees().contains(student))
                throw new IllegalStateException("You are not authorized to access this resource");
        }

        List<LessonResourceDto> lessonResourceDtos = new ArrayList<>();
        for (LessonResource lr : lesson.getLessonResources()) {
            lessonResourceDtos.add(new LessonResourceDto(lr));
        }
        return lessonResourceDtos;
    }

    public byte[] getFileResources(Course course, User user, int lessonId, int resourceId) {
        Lesson lesson = getLessonbyId(course, lessonId);
        if (lesson == null) throw new IllegalStateException("Lesson not found");

        if (user instanceof Instructor && ((Instructor) user).getId() != course.getInstructor().getId())
            throw new IllegalStateException("You are not authorized to access this resource(I)");

        if (user instanceof Student) {
            Student student = (Student) user;
            if (!student.getEnrolled_courses().contains(course) || !lesson.getAttendees().contains(student))
                throw new IllegalStateException("You are not authorized to access this resource");
        }

        LessonResource resource = lesson.getLessonResources().stream()
                .filter(r -> r.getResource_id() == resourceId)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Resource not found"));

        try {
            return Files.readAllBytes(resourcesPath.resolve(resource.getFile_name()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Set<CourseDto> enrollCourse(String courseName, User user) {
        Student student = (Student) user;
        Course course = getCourse(courseName);

        if (course == null) throw new IllegalStateException("Course not found");
        if (course.getEnrolled_students().contains(student))
            throw new IllegalStateException("You are already enrolled in this course");

        course.addStudent(student);
        Instructor instructor = course.getInstructor();

        Notification studentNotification = notificationService.createNotification(
                "You have successfully enrolled in Course: " + courseName
        );
        notificationService.addNotifcationStudent(studentNotification, student);

        emailService.sendEmail(
                student.getEmail(),
                student.getName(),
                "Enrollment Confirmation",
                "You have successfully enrolled in Course: " + courseName,
                instructor.getName()
        );

        Notification instructorNotification = notificationService.createNotification(
                "Student:" + student.getName() + " Enrolled in Course: " + courseName
        );
        notificationService.addNotificationInstructor(instructorNotification, instructor);

        courseRepository.save(course);

        Set<CourseDto> enrolledCoursesDto = new HashSet<>();
        for (Course c : student.getEnrolled_courses()) {
            enrolledCoursesDto.add(new CourseDto(c));
        }
        return enrolledCoursesDto;
    }

    public Set<StudentDto> getEnrolledStd(String courseName) {
        Course course = getCourse(courseName);
        if (course == null) throw new IllegalStateException("Course not found");

        Set<StudentDto> dtos = new HashSet<>();
        for (Student s : course.getEnrolled_students()) {
            dtos.add(new StudentDto(s));
        }
        return dtos;
    }

    public Set<Student> getEnrolledStd(Course course){
        return course.getEnrolled_students();
    }

    public void removeEnrolledstd(Course course, Instructor instructor, int studentId) {
        User user = userService.getUserById(studentId);
        if (!(user instanceof Student)) throw new IllegalStateException("Student not found");
        course.removeStudent((Student) user);
        courseRepository.save(course);
    }

    public int generateOTP(Course course, Set<Student> students, Instructor instructor, Lesson lesson, int duration) {
        int otp = new Random().nextInt(900000) + 100000;
        LessonOTP lessonOTP = new LessonOTP();
        lessonOTP.setOtpValue(otp);
        lessonOTP.setExpireAt(LocalDateTime.now().plusDays(duration));
        lesson.addLessonOTP(lessonOTP);

        for (Student student : students) {
            emailService.sendOTP(
                    student.getEmail(),
                    student.getName(),
                    lesson.getTitle(),
                    lesson.getDescription(),
                    duration,
                    instructor.getName(),
                    otp,
                    course.getName()
            );
        }

        courseRepository.save(course);
        return otp;
    }

    public LessonDto attendLesson(Course course, Student student, Lesson lesson, int givenOtp) {
        LessonOTP lessonOTP = lesson.getLessonOTP();

        if (lessonOTP.getOtpValue() != givenOtp)
            throw new IllegalStateException("Incorrect OTP");

        if (lessonOTP.getExpireAt().isBefore(LocalDateTime.now()))
            throw new IllegalStateException("OTP expired");

        lesson.addAttendee(student);

        Notification notificationMessage = notificationService.createNotification(
                "Student:" + student.getName() + " Attends Lesson: " + lesson.getTitle()
        );
        notificationService.addNotificationInstructor(notificationMessage, course.getInstructor());

        courseRepository.save(course);
        return new LessonDto(lesson);
    }

    public Set<LessonDto> getLessonAttended(Course course, Student student) {
        Set<LessonDto> dtos = new HashSet<>();
        for (Lesson l : student.getLessonAttended()) {
            if (l.getCourse().getCourseId() == course.getCourseId()) {
                dtos.add(new LessonDto(l));
            }
        }
        return dtos;
    }

    public List<StudentDto> getAttendance(Lesson lesson) {
        List<StudentDto> dtos = new ArrayList<>();
        for (Student s : lesson.getAttendees()) {
            dtos.add(new StudentDto(s));
        }
        return dtos;
    }
}
