package com.omyadav.repodoctor.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public final class AnalysisScope {
    private final int commitScopeLimit;
    private final int sourceFileScopeLimit;
    private final boolean treeWasTruncated;
    private final String scopeNote;

    public AnalysisScope(int commitScopeLimit, int sourceFileScopeLimit, boolean treeWasTruncated, String scopeNote) {
        this.commitScopeLimit = commitScopeLimit;
        this.sourceFileScopeLimit = sourceFileScopeLimit;
        this.treeWasTruncated = treeWasTruncated;
        this.scopeNote = scopeNote;
    }

    public int getCommitScopeLimit() {
        return commitScopeLimit;
    }

    public int getSourceFileScopeLimit() {
        return sourceFileScopeLimit;
    }

    public boolean isTreeWasTruncated() {
        return treeWasTruncated;
    }

    public String getScopeNote() {
        return scopeNote;
    }
}
