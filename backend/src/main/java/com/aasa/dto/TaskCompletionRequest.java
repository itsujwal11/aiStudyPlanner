package com.aasa.dto;

import lombok.Data;

@Data
public class TaskCompletionRequest {
    private Long topicId;
    private String activityType;
    private boolean completed;
    private Integer sessionIndex;
}
