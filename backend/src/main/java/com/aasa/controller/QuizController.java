package com.aasa.controller;

import com.aasa.dto.QuizDto;
import com.aasa.dto.QuizSubmissionRequest;
import com.aasa.dto.QuizSubmissionResponse;
import com.aasa.entity.Quiz;
import com.aasa.entity.QuizAttempt;
import com.aasa.entity.Topic;
import com.aasa.entity.User;
import com.aasa.repository.QuizAttemptRepository;
import com.aasa.service.AuthService;
import com.aasa.service.QuizEngineService;
import com.aasa.service.StudyProgressService;
import com.aasa.service.TopicAnalysisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Logger;

@RestController
@RequestMapping("/api/quizzes")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class QuizController {

    private static final Logger logger = Logger.getLogger(QuizController.class.getName());

    @Autowired
    private QuizEngineService quizEngineService;

    @Autowired
    private TopicAnalysisService topicAnalysisService;

    @Autowired
    private AuthService authService;

    @Autowired
    private StudyProgressService studyProgressService;

    @Autowired
    private QuizAttemptRepository quizAttemptRepository;

    @GetMapping("/topic/{topicId}")
    public ResponseEntity<List<QuizDto>> getQuizzesByTopic(@PathVariable Long topicId) {
        try {
            logger.info("Fetching quizzes for topic ID: " + topicId);
            List<QuizDto> quizzes = quizEngineService.getQuizzesByTopic(topicId);
            logger.info("Found " + quizzes.size() + " quizzes for topic");
            return ResponseEntity.ok(quizzes);
        } catch (Exception e) {
            logger.severe("Error fetching quizzes: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{quizId}")
    public ResponseEntity<QuizDto> getQuiz(@PathVariable Long quizId) {
        try {
            logger.info("Fetching quiz ID: " + quizId);
            Quiz quiz = quizEngineService.getQuizById(quizId);
            return ResponseEntity.ok(convertToDto(quiz));
        } catch (Exception e) {
            logger.severe("Error fetching quiz: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @PostMapping("/{quizId}/submit")
    public ResponseEntity<QuizSubmissionResponse> submitQuiz(
            @PathVariable Long quizId,
            @RequestBody QuizSubmissionRequest request,
            Authentication authentication) {
        try {
            logger.info("Submitting quiz ID: " + quizId);

            // Validate required fields
            if (request.getSelectedAnswer() == null || request.getSelectedAnswer().trim().isEmpty()) {
                logger.warning("Selected answer is null or empty");
                return ResponseEntity.badRequest().build();
            }

            if (request.getTimeTakenSeconds() == null || request.getTimeTakenSeconds() < 0) {
                logger.warning("Invalid or missing timeTakenSeconds");
                return ResponseEntity.badRequest().build();
            }

            User user = authService.getUserByEmail(authentication.getName());
            Quiz quiz = quizEngineService.getQuizById(quizId);

            String normalizedSelectedAnswer = request.getSelectedAnswer().trim();
            String correctAnswer = quiz.getCorrectAnswer();
            Boolean isCorrect = correctAnswer.equalsIgnoreCase(normalizedSelectedAnswer);
            Double marksObtained = isCorrect ? 1.0 : 0.0;

            logger.info("Quiz submission - Correct: " + isCorrect + ", Selected: [" + normalizedSelectedAnswer + "], Expected: [" + correctAnswer + "]");

            QuizAttempt attempt = QuizAttempt.builder()
                    .user(user)
                    .quiz(quiz)
                    .selectedAnswer(request.getSelectedAnswer())
                    .isCorrect(isCorrect)
                    .marksObtained(marksObtained)
                    .timeTakenSeconds(request.getTimeTakenSeconds())
                    .build();

            quizAttemptRepository.save(attempt);
            logger.info("Quiz attempt saved");

            Topic topic = quiz.getTopic();
            studyProgressService.updateProgressAfterQuizAttempt(user, topic, attempt);
            logger.info("Study progress updated");

            QuizSubmissionResponse response = QuizSubmissionResponse.builder()
                    .isCorrect(isCorrect)
                    .correctAnswer(correctAnswer)
                    .explanation(quiz.getExplanation())
                    .marksObtained(marksObtained)
                    .build();

            logger.info("Quiz submission completed successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.severe("Error submitting quiz: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private QuizDto convertToDto(Quiz quiz) {
        return QuizDto.builder()
                .id(quiz.getId())
                .topicId(quiz.getTopic().getId())
                .question(quiz.getQuestion())
                .optionA(quiz.getOptionA())
                .optionB(quiz.getOptionB())
                .optionC(quiz.getOptionC())
                .optionD(quiz.getOptionD())
                .difficulty(quiz.getDifficulty().toString())
                .build();
    }
}
