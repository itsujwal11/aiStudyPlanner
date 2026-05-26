package com.aasa.dto;

import java.util.List;

public class StudyStrategy {
    private int studyDurationMin;
    private int sessions;
    private List<String> methods;
    private int questions;
    private int revisionFrequency;

    public StudyStrategy() {}

    public StudyStrategy(int studyDurationMin, int sessions, List<String> methods, int questions, int revisionFrequency) {
        this.studyDurationMin = studyDurationMin;
        this.sessions = sessions;
        this.methods = methods;
        this.questions = questions;
        this.revisionFrequency = revisionFrequency;
    }

    public int getStudyDurationMin() { return studyDurationMin; }
    public void setStudyDurationMin(int studyDurationMin) { this.studyDurationMin = studyDurationMin; }
    public int getSessions() { return sessions; }
    public void setSessions(int sessions) { this.sessions = sessions; }
    public List<String> getMethods() { return methods; }
    public void setMethods(List<String> methods) { this.methods = methods; }
    public int getQuestions() { return questions; }
    public void setQuestions(int questions) { this.questions = questions; }
    public int getRevisionFrequency() { return revisionFrequency; }
    public void setRevisionFrequency(int revisionFrequency) { this.revisionFrequency = revisionFrequency; }
}
