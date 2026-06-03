package com.aasa.service;

import com.aasa.dto.DashboardDto;
import com.aasa.dto.StudyProgressDto;
import com.aasa.entity.PdfDocument;
import com.aasa.entity.StudyProgress;
import com.aasa.entity.Topic;
import com.aasa.entity.User;
import com.aasa.repository.PdfDocumentRepository;
import com.aasa.repository.QuizAttemptRepository;
import com.aasa.repository.QuizRepository;
import com.aasa.repository.StudyProgressRepository;
import com.aasa.repository.TopicRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
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

    @Autowired
    private StudyProgressRepository studyProgressRepository;

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

    public DashboardDto generatePdfDashboard(User user, Long pdfId) {
        if (user == null || pdfId == null) {
            return emptyDashboard();
        }

        PdfDocument pdf = pdfDocumentRepository.findById(pdfId).orElse(null);
        if (pdf == null) {
            return emptyDashboard();
        }

        List<Topic> pdfTopics = topicRepository.findByPdfDocumentId(pdfId);
        List<Long> topicIds = pdfTopics.stream().map(Topic::getId).collect(Collectors.toList());

        int totalTopics = pdfTopics.size();

        int totalQuizzes = 0;
        for (Topic t : pdfTopics) {
            totalQuizzes += t.getQuizzes() != null ? t.getQuizzes().size() : 0;
        }

        Double averageScore = quizAttemptRepository.getAverageScoreByPdf(user.getId(), pdfId);
        if (averageScore == null) averageScore = 0.0;

        int daysUntilExam = pdf.getExamDate() != null
                ? (int) ChronoUnit.DAYS.between(LocalDate.now(), pdf.getExamDate())
                : 0;

        List<StudyProgressDto> rankedTopics = studyProgressRepository
                .findByUserIdAndPdfIdOrderByPriority(user.getId(), pdfId)
                .stream()
                .map(this::toProgressDto)
                .collect(Collectors.toList());

        List<StudyProgressDto> weakTopics = studyProgressRepository
                .findByUserIdAndPdfId(user.getId(), pdfId)
                .stream()
                .filter(sp -> sp.getWeaknessLevel() == StudyProgress.WeaknessLevel.HIGH ||
                             sp.getWeaknessLevel() == StudyProgress.WeaknessLevel.MEDIUM)
                .sorted((a, b) -> b.getTopic().getPriorityScore().compareTo(a.getTopic().getPriorityScore()))
                .map(this::toProgressDto)
                .collect(Collectors.toList());

        Double overallCompletion = studyProgressRepository.getAverageCompletionByPdf(user.getId(), pdfId);
        if (overallCompletion == null) overallCompletion = 0.0;

        return DashboardDto.builder()
                .totalPdfs(1)
                .totalTopics(totalTopics)
                .totalQuizzes(totalQuizzes)
                .averageScore(averageScore)
                .daysUntilExam(daysUntilExam)
                .rankedTopics(rankedTopics != null ? rankedTopics : List.of())
                .weakTopics(weakTopics != null ? weakTopics : List.of())
                .overallCompletionPercentage(overallCompletion)
                .build();
    }

    private StudyProgressDto toProgressDto(StudyProgress progress) {
        return StudyProgressDto.builder()
                .topicId(progress.getTopic().getId())
                .topicTitle(progress.getTopic().getTitle())
                .weaknessLevel(progress.getWeaknessLevel().toString())
                .completionPercentage(progress.getCompletionPercentage())
                .bestScore(progress.getBestScore())
                .totalAttempts(progress.getTotalAttempts())
                .correctAttempts(progress.getCorrectAttempts())
                .priorityScore(progress.getTopic().getPriorityScore())
                .build();
    }

    private DashboardDto emptyDashboard() {
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
