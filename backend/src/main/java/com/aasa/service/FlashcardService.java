package com.aasa.service;

import com.aasa.entity.Flashcard;
import com.aasa.entity.FlashcardReview;
import com.aasa.entity.Topic;
import com.aasa.entity.User;
import com.aasa.repository.FlashcardRepository;
import com.aasa.repository.FlashcardReviewRepository;
import com.aasa.repository.TopicRepository;
import com.aasa.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Service
public class FlashcardService {

    private static final Logger logger = Logger.getLogger(FlashcardService.class.getName());

    @Autowired
    private FlashcardRepository flashcardRepository;

    @Autowired
    private FlashcardReviewRepository flashcardReviewRepository;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private UserRepository userRepository;

    public List<Flashcard> getFlashcardsByTopic(Long topicId) {
        return flashcardRepository.findByTopicId(topicId);
    }

    public List<Flashcard> getFlashcardsByPdf(Long pdfId) {
        return flashcardRepository.findByTopicPdfDocumentId(pdfId);
    }

    @Transactional
    public Flashcard createFlashcard(Topic topic, String front, String back, Double difficulty) {
        Flashcard card = Flashcard.builder()
                .topic(topic)
                .frontText(front)
                .backText(back)
                .difficultyEst(difficulty != null ? difficulty : 0.5)
                .build();
        return flashcardRepository.save(card);
    }

    @Transactional
    public FlashcardReview reviewFlashcard(Long flashcardId, User user, int rating) {
        if (rating < 1 || rating > 4) {
            throw new IllegalArgumentException("Rating must be between 1 and 4");
        }

        Flashcard card = flashcardRepository.findById(flashcardId)
                .orElseThrow(() -> new RuntimeException("Flashcard not found"));

        FlashcardReview review = flashcardReviewRepository
                .findByFlashcardIdAndUserId(flashcardId, user.getId())
                .orElseGet(() -> {
                    logger.info("Creating new flashcard review for card: " + flashcardId);
                    return FlashcardReview.builder()
                            .flashcard(card)
                            .user(user)
                            .box(0)
                            .intervalDays(0)
                            .efactor(2.5)
                            .repetitions(0)
                            .build();
                });

        applySm2(review, rating);
        review.setLastRating(rating);
        review.setNextReviewAt(LocalDate.now().plusDays(review.getIntervalDays()));

        return flashcardReviewRepository.save(review);
    }

    private void applySm2(FlashcardReview review, int rating) {
        double ef = review.getEfactor() != null ? review.getEfactor() : 2.5;
        int rep = review.getRepetitions() != null ? review.getRepetitions() : 0;
        int interval;

        if (rating < 3) {
            rep = 0;
            interval = 1;
            review.setBox(0);
        } else {
            if (rep == 0) interval = 1;
            else if (rep == 1) interval = 6;
            else interval = (int) Math.ceil(review.getIntervalDays() * ef);
            rep++;
            int newBox = Math.min(review.getBox() + 1, 5);
            review.setBox(newBox);
        }

        ef = ef + (0.1 - (4 - rating) * (0.08 + (4 - rating) * 0.02));
        if (ef < 1.3) ef = 1.3;

        review.setRepetitions(rep);
        review.setIntervalDays(interval);
        review.setEfactor(ef);
    }

    @Transactional
    public List<FlashcardReview> getDueReviews(Long userId) {
        List<FlashcardReview> existingDue = flashcardReviewRepository.findDueForReview(userId, LocalDate.now());
        Set<Long> reviewedIds = existingDue.stream()
                .map(r -> r.getFlashcard().getId()).collect(Collectors.toSet());

        List<Topic> userTopics = topicRepository.findByUserIdOrderByPriority(userId);
        List<Flashcard> allFlashcards = new ArrayList<>();
        for (Topic t : userTopics) {
            allFlashcards.addAll(flashcardRepository.findByTopicId(t.getId()));
        }

        // Auto-generate flashcards for topics if none exist yet (existing PDFs analyzed before flashcard feature)
        if (allFlashcards.isEmpty() && !userTopics.isEmpty()) {
            logger.info("No flashcards found — auto-generating for " + userTopics.size() + " topics");
            for (Topic topic : userTopics) {
                generateDefaultFlashcards(topic);
            }
            allFlashcards.clear();
            for (Topic t : userTopics) {
                allFlashcards.addAll(flashcardRepository.findByTopicId(t.getId()));
            }
        }

        List<FlashcardReview> result = new ArrayList<>(existingDue);

        if (!allFlashcards.isEmpty()) {
            User flashcardUser = userRepository.findById(userId).orElse(null);
            for (Flashcard card : allFlashcards) {
                if (!reviewedIds.contains(card.getId())) {
                    FlashcardReview newReview = FlashcardReview.builder()
                            .flashcard(card)
                            .user(flashcardUser)
                            .box(0)
                            .intervalDays(0)
                            .efactor(2.5)
                            .repetitions(0)
                            .nextReviewAt(LocalDate.now())
                            .lastRating(0)
                            .build();
                    flashcardReviewRepository.save(newReview);
                    result.add(newReview);
                }
            }
        }

        return result;
    }

    private void generateDefaultFlashcards(Topic topic) {
        String desc = topic.getDescription();
        if (desc == null || desc.isBlank()) {
            desc = "Study topic: " + topic.getTitle();
        }

        createFlashcard(topic, "What is " + topic.getTitle() + "?", desc,
                topic.getComplexityScore());

        String[] sentences = desc.split("[.!?]");
        if (sentences.length >= 2) {
            String second = sentences[1].trim();
            if (!second.isBlank()) {
                createFlashcard(topic, "Explain: " + topic.getTitle(), second,
                        topic.getComplexityScore());
            }
        }

        logger.info("Auto-generated flashcards for topic: " + topic.getTitle());
    }
}
