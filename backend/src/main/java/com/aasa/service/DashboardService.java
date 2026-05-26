package com.aasa.service;

import com.aasa.dto.DashboardDto;
import com.aasa.dto.StudyProgressDto;
import com.aasa.entity.PdfDocument;
import com.aasa.entity.Topic;
import com.aasa.entity.User;
import com.aasa.repository.PdfDocumentRepository;
import com.aasa.repository.QuizAttemptRepository;
import com.aasa.repository.QuizRepository;
import com.aasa.repository.TopicRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class DashboardService {

    @Autowired
    private PdfDocumentRepository pdfDocumentRepository;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private QuizRepository quizRepository;

    @Autowired
    private QuizAttemptRepository quizAttemptRepository;

    @Autowired
    private StudyProgressService studyProgressService;

    public DashboardDto generateDashboard(User user) {
        if (user == null) {
            return DashboardDto.builder()
                    .totalPdfs(0)
                    .totalTopics(0)
                    .totalQuizzes(0)
                    .averageScore(0.0)
                    .daysUntilExam(0)
                    .rankedTopics(List.of())
                    .weakTopics(List.of())
                    .overallCompletionPercentage(0.0)
                    .build();
        }

        List<PdfDocument> dbPdfs = pdfDocumentRepository.findByUserId(user.getId());
        List<PdfDocument> safePdfs = dbPdfs != null ? dbPdfs : List.of();

        int totalPdfs = safePdfs.size();
        int totalTopics = (int) topicRepository.findByUserIdOrderByPriority(user.getId()).stream().count();

        int totalQuizzes = 0;
        List<com.aasa.entity.Quiz> quizzes = quizRepository.findAll();
        if (quizzes != null) {
            for (com.aasa.entity.Quiz q : quizzes) {
                if (q == null || q.getTopic() == null) {
                    continue;
                }
                Topic topic = q.getTopic();

                boolean found = false;
                if (safePdfs != null) {
                    for (PdfDocument p : safePdfs) {
                        if (p == null) continue;
                        List<Topic> topics = p.getTopics();
                        if (topics != null && topics.contains(topic)) {
                            found = true;
                            break;
                        }
                    }
                }

                if (found) {
                    totalQuizzes++;
                }
            }
        }

        Double averageScore = quizAttemptRepository.getAverageScore(user.getId());
        if (averageScore == null) {
            averageScore = 0.0;
        }

        int daysUntilExam = calculateDaysUntilExam(safePdfs);

        List<StudyProgressDto> rankedTopics = studyProgressService.getUserProgressRankedByPriority(user.getId());
        List<StudyProgressDto> weakTopics = studyProgressService.getWeakTopics(user.getId());
        Double overallCompletion = studyProgressService.getAverageCompletion(user.getId());

        return DashboardDto.builder()
                .totalPdfs(totalPdfs)
                .totalTopics(totalTopics)
                .totalQuizzes(totalQuizzes)
                .averageScore(averageScore)
                .daysUntilExam(daysUntilExam)
                .rankedTopics(rankedTopics != null ? rankedTopics : List.of())
                .weakTopics(weakTopics != null ? weakTopics : List.of())
                .overallCompletionPercentage(overallCompletion != null ? overallCompletion : 0.0)
                .build();
    }

    private int calculateDaysUntilExam(List<PdfDocument> pdfs) {
        if (pdfs == null || pdfs.isEmpty()) {
            return 0;
        }

        LocalDate nearestExamDate = pdfs.stream()
                .map(PdfDocument::getExamDate)
                .filter(d -> d != null)
                .min(LocalDate::compareTo)
                .orElse(null);

        if (nearestExamDate == null) {
            return 0;
        }

        return (int) ChronoUnit.DAYS.between(LocalDate.now(), nearestExamDate);
    }
}
