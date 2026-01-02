package com.dev.LMS.service;

import com.dev.LMS.dto.*;
import com.dev.LMS.model.*;
import com.dev.LMS.repository.CourseRepository;
import com.dev.LMS.repository.QuizSubmissionRepositry;
import com.dev.LMS.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContextException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

@Service
public class AssessmentService {

    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final QuizSubmissionRepositry quizSubmissionRepositry;
    private final NotificationService notificationService;
    private final EmailService emailService;

    @Value("${file.upload.base-path.assignment-submissions}")
    private String UPLOAD_DIR;

    public AssessmentService(CourseRepository courseRepository,
                             UserRepository userRepository,
                             QuizSubmissionRepositry quizSubmissionRepositry,
                             NotificationService notificationService,
                             EmailService emailService) {
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.quizSubmissionRepositry = quizSubmissionRepositry;
        this.notificationService = notificationService;
        this.emailService = emailService;
    }

    // ------------------ QUESTIONS ------------------
    public void createQuestion(String courseName, Question question) {
        Course course = courseRepository.findByName(courseName)
                .orElseThrow(() -> new IllegalArgumentException("Course not found: " + courseName));
        course.addQuestion(question);
        courseRepository.save(course);
    }

    public QuestionDto getQuestionById(String courseName, int questionId) {
        Course course = courseRepository.findByName(courseName)
                .orElseThrow(() -> new IllegalArgumentException("Course not found: " + courseName));
        return course.getQuestions().stream()
                .filter(q -> q.getId() == questionId)
                .findFirst()
                .map(QuestionDto::toDto)
                .orElseThrow(() -> new IllegalArgumentException("No question by this Id: " + questionId));
    }

    public List<QuestionDto> getQuestions(String courseName) {
        Course course = courseRepository.findByName(courseName)
                .orElseThrow(() -> new IllegalArgumentException("Course not found: " + courseName));
        List<Question> questions = course.getQuestions();
        if (questions.isEmpty()) throw new IllegalArgumentException("No questions available for this course.");
        List<QuestionDto> dtos = new ArrayList<>();
        for (Question q : questions) dtos.add(QuestionDto.toDto(q));
        return dtos;
    }

    // ------------------ QUIZZES ------------------
    public void createQuiz(String courseName, Quiz newQuiz) {
        Course course = courseRepository.findByName(courseName)
                .orElseThrow(() -> new IllegalArgumentException("Course not found: " + courseName));
        course.addQuiz(newQuiz);

        Notification notification = notificationService.createNotification(
                "A new quiz " + newQuiz.getQuizTitle() + " was added to your course work"
        );

        String subject = "New Quiz Added";
        String content = "A new quiz " + newQuiz.getQuizTitle() + " added in Course: " + course.getName();

        for (Student student : course.getEnrolled_students()) {
            notificationService.addNotifcationStudent(notification, student);
            emailService.sendEmail(student.getEmail(), student.getName(), subject, content, course.getInstructor().getName());
        }

        courseRepository.save(course);
    }

    public QuizSubmissionDto generateQuiz(String courseName, String quizTitle, Student student) {
        Course course = courseRepository.findByName(courseName)
                .orElseThrow(() -> new IllegalArgumentException("Course not found: " + courseName));

        Quiz quiz = course.getQuizzes().stream()
                .filter(q -> q.getQuizTitle().equals(quizTitle))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("This quiz does not exist."));

        List<Question> allQuestions = new ArrayList<>(course.getQuestions());
        if (allQuestions.isEmpty()) throw new IllegalStateException("No questions available for this course.");

        Collections.shuffle(allQuestions);
        List<Question> selectedQuestions = allQuestions.subList(0, Math.min(2, allQuestions.size()));

        QuizSubmission submission = new QuizSubmission();
        submission.setQuestions(selectedQuestions);
        submission.setSubmittedQuestions(new ArrayList<>());
        submission.setGrade(0);
        submission.setStudent(student);
        submission.setQuiz(quiz);

        for (Question q : selectedQuestions) q.addSubmission(submission);

        student.addQuizSubmission(submission);
        quiz.addQuizSubmission(submission);

        quizSubmissionRepositry.save(submission);
        courseRepository.save(course);

        return QuizSubmissionDto.toDto(submission);
    }

    public void submitQuiz(String courseName, String quizTitle, List<SubmittedQuestion> studentSubmittedQuestions, Student student) {
        Course course = courseRepository.findByName(courseName)
                .orElseThrow(() -> new IllegalArgumentException("Course not found: " + courseName));

        Quiz quiz = course.getQuizzes().stream()
                .filter(q -> q.getQuizTitle().equals(quizTitle))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("This quiz does not exist."));

        QuizSubmission submission = quiz.getSubmissions().stream()
                .filter(s -> s.getStudent().getId() == student.getId())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("There is no submission."));

        if (studentSubmittedQuestions == null || studentSubmittedQuestions.isEmpty())
            throw new IllegalStateException("Your submission is empty.");

        List<SubmittedQuestion> submittedQuestions = new ArrayList<>();
        for (int i = 0; i < studentSubmittedQuestions.size(); i++) {
            SubmittedQuestion sq = studentSubmittedQuestions.get(i);
            if (sq.getStudentAnswer() == null)
                throw new IllegalStateException("Student answer cannot be null");
            sq.setSubmission(submission);
            sq.setQuestion(submission.getQuestions().get(i));
            submittedQuestions.add(sq);
        }

        submission.setSubmittedQuestions(submittedQuestions);
        courseRepository.save(course);
    }

    public void gradeQuiz(String quizTitle, String courseName) {
        Course course = courseRepository.findByName(courseName)
                .orElseThrow(() -> new IllegalArgumentException("Course not found: " + courseName));

        Quiz quiz = course.getQuizzes().stream()
                .filter(q -> q.getQuizTitle().equals(quizTitle))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("This quiz does not exist."));

        for (QuizSubmission submission : quiz.getSubmissions()) {
            int grade = 0;
            for (SubmittedQuestion sq : submission.getSubmittedQuestions()) {
                if (sq.getStudentAnswer().equals(sq.getQuestion().getCorrectAnswer())) grade++;
            }
            submission.setGrade(grade);
        }

        courseRepository.save(course);
    }

    public int getQuizGrade(String quizTitle, String courseName, Student student) {
        gradeQuiz(quizTitle, courseName);

        Course course = courseRepository.findByName(courseName)
                .orElseThrow(() -> new IllegalArgumentException("Course not found: " + courseName));

        Quiz quiz = course.getQuizzes().stream()
                .filter(q -> q.getQuizTitle().equals(quizTitle))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("This quiz does not exist."));

        return quiz.getSubmissions().stream()
                .filter(s -> s.getStudent().equals(student))
                .map(QuizSubmission::getGrade)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No submission for this student: " + student.getName()));
    }

    // ------------------ ASSIGNMENTS ------------------
    public AssignmentDto addAssignment(Course course, Assignment assignment, Instructor instructor) {
        if (!instructor.getCreatedCourses().contains(course))
            throw new IllegalStateException("You are not authorized to add assignments to this course");

        course.addAssignment(assignment);

        Notification notification = notificationService.createNotification(
                "A new assignment " + assignment.getTitle() + " was added to your course work"
        );

        String subject = "New Assignment Added";
        String content = "A new assignment " + assignment.getTitle() + " added in Course: " + course.getName();

        for (Student student : course.getEnrolled_students()) {
            notificationService.addNotifcationStudent(notification, student);
            emailService.sendEmail(student.getEmail(), student.getName(), subject, content, course.getInstructor().getName());
        }

        courseRepository.save(course);
        return new AssignmentDto(assignment);
    }

    public List<AssignmentDto> getAssignments(Course course, User user) {
        List<Assignment> assignments;
        if (user instanceof Instructor instructor) {
            if (!instructor.getCreatedCourses().contains(course))
                throw new IllegalStateException("You are not the instructor of this course");
            assignments = course.getAssignments();
        } else {
            Student student = (Student) user;
            if (!student.getEnrolled_courses().contains(course))
                throw new IllegalStateException("You are not enrolled in this course");
            assignments = course.getAssignments();
        }

        List<AssignmentDto> dtos = new ArrayList<>();
        for (Assignment a : assignments) dtos.add(new AssignmentDto(a));
        return dtos;
    }

    // Методы для работы с AssignmentSubmission
    public List<AssignmentSubmissionDto> getSubmissions(Assignment assignment) {
        List<AssignmentSubmissionDto> dtos = new ArrayList<>();
        for (AssignmentSubmission s : assignment.getSubmissions()) dtos.add(new AssignmentSubmissionDto(s));
        return dtos;
    }

    public AssignmentSubmission getSubmission(Assignment assignment, int submissionId) {
        return assignment.getSubmissions().stream()
                .filter(s -> s.getSubmissionId() == submissionId)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Submission not found"));
    }

    public Assignment getAssignment(Course course, User user, int assignmentId) {
        List<Assignment> assignments;
        if (user instanceof Instructor instructor) {
            if (!instructor.getCreatedCourses().contains(course))
                throw new IllegalStateException("You are not the instructor of this course");
            assignments = course.getAssignments();
        } else {
            Student student = (Student) user;
            if (!student.getEnrolled_courses().contains(course))
                throw new IllegalStateException("You are not enrolled in this course");
            assignments = course.getAssignments();
        }

        return assignments.stream()
                .filter(a -> a.getAssignmentId() == assignmentId)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Assignment not found"));
    }

    public String uploadSubmissionFile(MultipartFile file, Assignment assignment, Student student) {
        String filePath = UPLOAD_DIR + file.getOriginalFilename();

        AssignmentSubmission submission = new AssignmentSubmission();
        submission.setAssignment(assignment);
        submission.setStudent(student);
        submission.setFileName(file.getOriginalFilename());
        submission.setFileType(file.getContentType());
        submission.setFilePath(filePath);

        student.addAssignmentSubmission(submission);
        userRepository.save(student);

        try {
            file.transferTo(new File(filePath));
        } catch (IOException e) {
            throw new RuntimeException("Unable to store the file at " + filePath);
        }

        return "File successfully uploaded to " + filePath;
    }

    public byte[] downloadSubmissionFile(Assignment assignment, int submissionId) {
        AssignmentSubmission sub = getSubmission(assignment, submissionId);
        try {
            return Files.readAllBytes(new File(sub.getFilePath()).toPath());
        } catch (IOException e) {
            throw new RuntimeException("Unable to load the file from " + sub.getFilePath());
        }
    }

    public AssignmentSubmissionDto setAssignmentGrade(AssignmentSubmission submission, Course course, Map<String, Integer> gradeMap) {
        submission.setGrade(gradeMap.get("grade"));
        submission.setGraded(true);

        Notification notification = notificationService.createNotification(
                "Your submission to " + submission.getAssignment().getTitle() + " got graded in Course: " + course.getName()
        );
        notificationService.addNotifcationStudent(notification, submission.getStudent());

        emailService.sendEmail(
                submission.getStudent().getEmail(),
                submission.getStudent().getName(),
                "Assignment Graded",
                "Your submission to " + submission.getAssignment().getTitle() + " got graded in Course: " + course.getName(),
                course.getInstructor().getName()
        );

        courseRepository.save(course);
        return new AssignmentSubmissionDto(submission);
    }

    public int getAssignmentGrade(Assignment assignment, Student student) {
        return student.getAssignmentSubmissions().stream()
                .filter(s -> s.getAssignment().equals(assignment))
                .findFirst()
                .map(s -> {
                    if (!s.isGraded()) throw new ApplicationContextException("Your submission wasn't graded yet.");
                    return s.getGrade();
                })
                .orElseThrow(() -> new IllegalStateException("You have no submissions for this assignment."));
    }

}
