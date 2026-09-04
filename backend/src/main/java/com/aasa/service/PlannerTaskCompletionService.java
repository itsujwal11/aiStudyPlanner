package com.aasa.service;

import com.aasa.entity.PlannerTaskCompletion;
import com.aasa.entity.User;
import com.aasa.repository.PlannerTaskCompletionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
public class PlannerTaskCompletionService {

    @Autowired
    private PlannerTaskCompletionRepository completionRepository;

    @Transactional(readOnly = true)
    public Map<String, Boolean> getCompletionsForDate(User user, LocalDate date) {
        List<PlannerTaskCompletion> completions =
                completionRepository.findByUserIdAndCompletionDate(user.getId(), date);
        return completions.stream()
                .collect(Collectors.toMap(
                        this::taskKey,
                        c -> Boolean.TRUE.equals(c.getCompleted()),
                        (left, right) -> left || right
                ));
    }

    @Transactional
    public void setCompletion(User user, LocalDate date, Long topicId, String activityType,
                              Integer sessionIndex, boolean completed) {
        int session = sessionIndex == null ? 0 : sessionIndex;
        var existing = completionRepository.findByUserIdAndCompletionDateAndTopicIdAndActivityTypeAndSessionIndex(
                user.getId(), date, topicId, activityType, session
        );

        if (existing.isPresent()) {
            PlannerTaskCompletion completion = existing.get();
            completion.setCompleted(completed);
            completionRepository.save(completion);
            return;
        }

        if (completed) {
            completionRepository.save(PlannerTaskCompletion.builder()
                    .user(user)
                    .topicId(topicId)
                    .activityType(activityType)
                    .completionDate(date)
                    .sessionIndex(session)
                    .completed(true)
                    .build());
        }
    }

    public String taskKey(Long topicId, String activityType, Integer sessionIndex) {
        int session = sessionIndex == null ? 0 : sessionIndex;
        return topicId + ":" + activityType + ":" + session;
    }

    private String taskKey(PlannerTaskCompletion completion) {
        return taskKey(completion.getTopicId(), completion.getActivityType(), completion.getSessionIndex());
    }

    public void deleteOldCompletions(User user, LocalDate beforeDate) {
        completionRepository.deleteByUserIdAndCompletionDateBefore(user.getId(), beforeDate);
    }
}