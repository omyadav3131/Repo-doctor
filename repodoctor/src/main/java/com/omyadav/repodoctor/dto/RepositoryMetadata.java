package com.omyadav.repodoctor.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public final class RepositoryMetadata {
    private final String owner;
    private final String repository;
    private final String repositoryUrl;
    private final String description;
    private final Integer stars;
    private final Integer forks;
    private final Integer watchers;
    private final Integer openIssues;
    private final String language;
    private final String license;
    private final String defaultBranch;
    private final String lastUpdated;
    private final boolean archived;
    private final int usefulFileCount;

    public RepositoryMetadata(String owner, String repository, String repositoryUrl,
            String description, Integer stars, Integer forks, Integer watchers,
            Integer openIssues, String language, String license,
            String defaultBranch, String lastUpdated, boolean archived,
            int usefulFileCount) {
        this.owner = owner;
        this.repository = repository;
        this.repositoryUrl = repositoryUrl;
        this.description = description;
        this.stars = stars;
        this.forks = forks;
        this.watchers = watchers;
        this.openIssues = openIssues;
        this.language = language;
        this.license = license;
        this.defaultBranch = defaultBranch;
        this.lastUpdated = lastUpdated;
        this.archived = archived;
        this.usefulFileCount = usefulFileCount;
    }

    public String getOwner() { return owner; }
    public String getRepository() { return repository; }
    public String getRepositoryUrl() { return repositoryUrl; }
    public String getDescription() { return description; }
    public Integer getStars() { return stars; }
    public Integer getForks() { return forks; }
    public Integer getWatchers() { return watchers; }
    public Integer getOpenIssues() { return openIssues; }
    public String getLanguage() { return language; }
    public String getLicense() { return license; }
    public String getDefaultBranch() { return defaultBranch; }
    public String getLastUpdated() { return lastUpdated; }
    public boolean isArchived() { return archived; }
    public int getUsefulFileCount() { return usefulFileCount; }
}
