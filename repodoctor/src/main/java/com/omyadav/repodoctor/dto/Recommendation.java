package com.omyadav.repodoctor.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public final class Recommendation {
    private final String priority;
    private final String category;
    private final String title;
    private final String description;
    private final List<String> affectedItems;

    public Recommendation(String priority, String category, String title, String description,
            List<String> affectedItems) {
        this.priority = priority;
        this.category = category;
        this.title = title;
        this.description = description;
        this.affectedItems = affectedItems == null ? List.of() : List.copyOf(affectedItems);
    }

    public String getPriority() {
        return priority;
    }

    public String getCategory() {
        return category;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public List<String> getAffectedItems() {
        return affectedItems;
    }
}
