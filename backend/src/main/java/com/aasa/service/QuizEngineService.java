package com.aasa.service;

import com.aasa.dto.AiAnalysisResponse;
import com.aasa.dto.QuizDto;
import com.aasa.entity.Quiz;
import com.aasa.entity.Topic;
import com.aasa.repository.QuizRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Service
public class QuizEngineService {

    private static final Logger logger = Logger.getLogger(QuizEngineService.class.getName());

    @Autowired
    private QuizRepository quizRepository;

    public List<Quiz> generateQuizzesForTopic(Topic topic, List<AiAnalysisResponse.TopicAnalysis.QuizQuestion> aiQuestions) {
        logger.info("Generating quizzes for topic: " + topic.getTitle() + " with " + aiQuestions.size() + " questions");

        logger.info("AI questions payload size for topic '" + topic.getTitle() + "': " + (aiQuestions == null ? 0 : aiQuestions.size()));

        // Add per-question debug so we can see why validation drops everything (leading to 0 quizzes saved)
        List<Quiz> validQuizzes = aiQuestions.stream()
                .filter(q -> {
                    boolean ok = validateQuestion(q);
                    if (!ok) {
                        logger.warning("Dropping invalid quiz question for topic '" + topic.getTitle() + "'. Question=" + (q != null ? q.getQuestion() : "<null>"));
                        if (q != null) {
                            logger.warning("Options=" + q.getOptions());
                            logger.warning("Answer=" + q.getAnswer());
                            logger.warning("Difficulty=" + q.getDifficulty());
                        }
                    }
                    return ok;
                })
                .map(question -> createQuizFromAiQuestion(question, topic))
                .collect(Collectors.toList());

        logger.info("Validated " + validQuizzes.size() + " out of " + aiQuestions.size() + " questions");

        if (validQuizzes.isEmpty()) {
            logger.warning("No valid quizzes to save for topic: " + topic.getTitle());
            return validQuizzes;
        }

        List<Quiz> savedQuizzes = quizRepository.saveAll(validQuizzes);
        logger.info("Saved " + savedQuizzes.size() + " quizzes for topic: " + topic.getTitle());
        return savedQuizzes;
    }

    private boolean validateQuestion(AiAnalysisResponse.TopicAnalysis.QuizQuestion question) {
        if (question == null) {
            logger.warning("Question is null");
            return false;
        }

        if (question.getQuestion() == null || question.getQuestion().trim().isEmpty()) {
            logger.warning("Question text is empty");
            return false;
        }

        if (question.getOptions() == null || question.getOptions().size() != 4) {
            logger.warning("Question must have exactly 4 options, found: " + (question.getOptions() != null ? question.getOptions().size() : 0));
            return false;
        }

        if (question.getAnswer() == null || question.getAnswer().trim().isEmpty()) {
            logger.warning("Answer is empty");
            return false;
        }

        // Case-insensitive check for answer existence in options.
        // Support both formats Gemini may return:
        //  1) answer is exactly one of the options (option text or "A"/"B"/"C"/"D")
        //  2) answer is "A"/"B"/"C"/"D" while options are full sentences.
        //     In this case we only validate that "A"/"B" maps to the 1st/2nd/3rd/4th option.
        String rawAnswer = question.getAnswer().trim();
        String answer = rawAnswer.toLowerCase();

        List<String> options = question.getOptions();

        boolean answerExists = options.stream()
                .filter(opt -> opt != null)
                .anyMatch(opt -> opt.trim().toLowerCase().equals(answer));

        if (!answerExists) {
            // Try A/B/C/D mapping
            String letter = answer;
            if (letter.length() == 1 && "abcd".contains(letter)) {
                // mapped option must exist
                int idx = switch (letter) {
                    case "a" -> 0;
                    case "b" -> 1;
                    case "c" -> 2;
                    case "d" -> 3;
                    default -> -1;
                };

                if (idx >= 0 && idx < options.size() && options.get(idx) != null
                        && !options.get(idx).trim().isEmpty()) {
                    // accept; we'll normalize correctAnswer later
                } else {
                    logger.warning("Correct answer '" + rawAnswer + "' is not mappable to options: " + options);
                    return false;
                }
            } else {
                logger.warning("Correct answer '" + rawAnswer + "' is not in options: " + options);
                return false;
            }
        }


        for (String option : question.getOptions()) {
            if (option == null || option.trim().isEmpty()) {
                logger.warning("One of the options is empty");
                return false;
            }
        }

        Set<String> uniqueOptions = new HashSet<>(question.getOptions());
        if (uniqueOptions.size() != 4) {
            logger.warning("Duplicate options found in: " + question.getOptions());
            return false;
        }

        return true;
    }

    private Quiz createQuizFromAiQuestion(AiAnalysisResponse.TopicAnalysis.QuizQuestion aiQuestion, Topic topic) {
        List<String> options = aiQuestion.getOptions();

        Quiz.DifficultyLevel difficulty = parseDifficulty(aiQuestion.getDifficulty());

        // Normalize the correct answer for comparison with the selected option text.
        // Supported Gemini formats:
        //  1) answer equals the full option text -> store that exact option text (normalized)
        //  2) answer is "A"/"B"/"C"/"D" -> map letter to option text
        //  3) answer is some other text -> fallback to raw trimmed lowercase
        String rawAnswer = aiQuestion.getAnswer().trim();
        String answerLower = rawAnswer.toLowerCase();

        String correctOptionText = null;
        if (options != null && !options.isEmpty()) {
            // if raw answer matches one of the options (case-insensitive), keep it
            for (String opt : options) {
                if (opt != null && opt.trim().equalsIgnoreCase(rawAnswer)) {
                    correctOptionText = opt.trim();
                    break;
                }
            }

            // if answer is a letter A/B/C/D, map to the corresponding option
            if (correctOptionText == null && rawAnswer.length() == 1 && "abcd".contains(answerLower)) {
                int idx = switch (answerLower) {
                    case "a" -> 0;
                    case "b" -> 1;
                    case "c" -> 2;
                    case "d" -> 3;
                    default -> -1;
                };
                if (idx >= 0 && idx < options.size() && options.get(idx) != null) {
                    correctOptionText = options.get(idx).trim();
                }
            }
        }

        if (correctOptionText == null) {
            correctOptionText = rawAnswer;
        }

        String normalizedAnswer = correctOptionText.toLowerCase();

        return Quiz.builder()
                .topic(topic)
                .question(aiQuestion.getQuestion())
                .optionA(options.get(0))
                .optionB(options.get(1))
                .optionC(options.get(2))
                .optionD(options.get(3))
                .correctAnswer(normalizedAnswer)
                .difficulty(difficulty)
                .explanation(aiQuestion.getExplanation())
                .build();

    }

    private Quiz.DifficultyLevel parseDifficulty(String difficulty) {
        if (difficulty == null) {
            return Quiz.DifficultyLevel.MEDIUM;
        }

        return switch (difficulty.toLowerCase().trim()) {
            case "easy" -> Quiz.DifficultyLevel.EASY;
            case "medium" -> Quiz.DifficultyLevel.MEDIUM;
            case "hard" -> Quiz.DifficultyLevel.HARD;
            default -> Quiz.DifficultyLevel.MEDIUM;
        };
    }

    @Transactional(readOnly = true)
    public List<QuizDto> getQuizzesByTopic(Long topicId) {
        logger.info("Fetching quizzes for topic ID: " + topicId);
        return quizRepository.findByTopicId(topicId)
                .stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Quiz getQuizById(Long quizId) {
        return quizRepository.findById(quizId)
                .orElseThrow(() -> new RuntimeException("Quiz not found with ID: " + quizId));
    }

    public long getQuizCountByTopic(Long topicId) {
        return quizRepository.countByTopicId(topicId);
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
