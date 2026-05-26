package com.aasa.dto;

import java.util.List;

public class ScheduleDay {
    private int day;
    private List<ScheduleBlock> blocks;

    public ScheduleDay() {}

    public ScheduleDay(int day, List<ScheduleBlock> blocks) {
        this.day = day;
        this.blocks = blocks;
    }

    public int getDay() { return day; }
    public void setDay(int day) { this.day = day; }
    public List<ScheduleBlock> getBlocks() { return blocks; }
    public void setBlocks(List<ScheduleBlock> blocks) { this.blocks = blocks; }
}
