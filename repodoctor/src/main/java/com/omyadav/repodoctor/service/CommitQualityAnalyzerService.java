package com.omyadav.repodoctor.service;

import com.omyadav.repodoctor.analysis.AnalysisStatus;
import com.omyadav.repodoctor.analysis.DimensionResult;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

@Service
public class CommitQualityAnalyzerService {

        private static final Set<String> GENERIC_MESSAGES = Set.of(
                        "update",
                        "updates",
                        "updated",
                        "fix",
                        "fixed",
                        "changes",
                        "change",
                        "done",
                        "final",
                        "test",
                        "testing",
                        "commit",
                        "initial commit",
                        "first commit",
                        "upload files",
                        "uploaded files",
                        "add files",
                        "added files",
                        "minor changes",
                        "small changes",
                        "bug fix",
                        "latest",
                        "new changes");

        private static final List<String> VAGUE_PHRASES = List.of(
                        "some changes",
                        "some updates",
                        "some fixes",
                        "new features",
                        "project changes",
                        "project files",
                        "code changes",
                        "updated code",
                        "update code",
                        "updated project",
                        "update project",
                        "misc changes",
                        "minor update",
                        "small update",
                        "various changes",
                        "few changes");

        private static final Set<String> ACTION_VERBS = Set.of(
                        "add", "added", "implement", "implemented", "create", "created",
                        "build", "built", "fix", "fixed", "resolve", "resolved",
                        "remove", "removed", "delete", "deleted", "refactor", "refactored",
                        "optimize", "optimized", "improve", "improved", "update", "updated",
                        "configure", "configured", "integrate", "integrated", "migrate", "migrated",
                        "replace", "replaced", "rename", "renamed", "handle", "handled",
                        "prevent", "prevented", "support", "supported", "enable", "enabled",
                        "disable", "disabled", "validate", "validated", "design", "designed",
                        "deploy", "deployed", "document", "documented", "revise", "revised",
                        "refine", "refined", "change", "changed", "expose", "exposed",
                        "clean", "cleaned", "cleanup", "restore", "restored", "move", "moved",
                        "reorganize", "reorganized", "simplify", "simplified", "enhance", "enhanced",
                        "correct", "corrected", "adjust", "adjusted", "introduce", "introduced",
                        "extend", "extended", "protect", "protected", "allow", "allowed");

        private static final Pattern MERGE_PATTERN = Pattern.compile(
                        "(?i)^merge\\s+(pull request|branch|remote-tracking branch|tag).*");

        private static final Pattern VERSION_ONLY_PATTERN = Pattern.compile(
                        "(?i)^v?\\d+(?:\\.\\d+){0,3}$");

        private static final Pattern CONVENTIONAL_COMMIT_PATTERN = Pattern.compile(
                        "(?i)^(feat|fix|docs|style|refactor|perf|test|build|ci|chore|revert)" +
                                        "(\\([a-z0-9._-]+\\))?!?:\\s+.+");

        public DimensionResult analyzeCommits(
                        List<Map<String, Object>> commits,
                        String repositoryType) {

                if (commits == null || commits.isEmpty()) {
                        return DimensionResult.notAnalyzable("No commits found");
                }

                int totalCommits = 0;
                int goodCommitCount = 0;
                int weakCommitCount = 0;
                int genericCommitCount = 0;
                int mergeCommitCount = 0;
                int conventionalCommitCount = 0;
                int totalMessageLength = 0;

                List<String> genericCommitMessages = new ArrayList<>();
                List<String> weakCommitMessages = new ArrayList<>();
                List<String> commitIssues = new ArrayList<>();
                List<String> reasons = new ArrayList<>();
                List<String> evidence = new ArrayList<>();

                Map<String, Integer> qualityMessageFrequency = new LinkedHashMap<>();

                for (Map<String, Object> commitData : commits) {
                        String message = extractCommitMessage(commitData);
                        if (message == null || message.isBlank())
                                continue;

                        String firstLine = extractFirstLine(message);
                        if (firstLine.isBlank())
                                continue;

                        totalCommits++;
                        totalMessageLength += firstLine.length();

                        CommitType commitType = classifyCommit(firstLine);

                        if (commitType != CommitType.MERGE) {
                                String normalized = normalize(firstLine);
                                qualityMessageFrequency.merge(normalized, 1, Integer::sum);
                        }

                        switch (commitType) {
                                case GOOD -> goodCommitCount++;
                                case WEAK -> {
                                        weakCommitCount++;
                                        weakCommitMessages.add(firstLine);
                                        commitIssues.add("Weak commit message: " + firstLine);
                                }
                                case GENERIC -> {
                                        genericCommitCount++;
                                        genericCommitMessages.add(firstLine);
                                        commitIssues.add("Generic commit message: " + firstLine);
                                }
                                case MERGE -> mergeCommitCount++;
                        }

                        if (commitType != CommitType.MERGE
                                        && CONVENTIONAL_COMMIT_PATTERN.matcher(firstLine).matches()) {
                                conventionalCommitCount++;
                        }
                }

                int nonMergeCount = totalCommits - mergeCommitCount;

                if (nonMergeCount == 0) {
                        return DimensionResult.notAnalyzable(
                                        "All analyzed commits are merge commits or no meaningful commits found");
                }

                AnalysisStatus status;
                double confidence;
                if (nonMergeCount >= 5) {
                        status = AnalysisStatus.SUCCESS;
                        confidence = Math.min(1.0, nonMergeCount / 20.0);
                } else {
                        status = AnalysisStatus.PARTIAL;
                        confidence = nonMergeCount / 20.0;
                }

                int repeatedMessageCount = calculateRepeatedMessageCount(qualityMessageFrequency, commitIssues);

                double averageMessageLength = totalCommits == 0 ? 0.0 : (double) totalMessageLength / totalCommits;

                int score = calculateScore(
                                totalCommits,
                                goodCommitCount,
                                weakCommitCount,
                                genericCommitCount,
                                mergeCommitCount,
                                conventionalCommitCount,
                                repeatedMessageCount,
                                repositoryType);

                // Build reasons based on findings
                if (goodCommitCount > 0) {
                        double goodRatio = (double) goodCommitCount / nonMergeCount;
                        if (goodRatio >= 0.7) {
                                reasons.add("✔ Excellent use of descriptive, action-oriented commits");
                        } else if (goodRatio >= 0.4) {
                                reasons.add("✔ Good mix of descriptive commits");
                        }
                }

                if (conventionalCommitCount > 0) {
                        reasons.add("✔ Conventional Commit format detected (" + conventionalCommitCount + " commits)");
                }

                if (genericCommitCount > 0) {
                        reasons.add("✘ Generic commit messages detected (" + genericCommitCount + " commits)");
                }
                
                if (repeatedMessageCount > 0) {
                        reasons.add("✘ Repeated identical commit messages used (" + repeatedMessageCount + " instances)");
                }
                
                if (weakCommitCount > 0 && genericCommitCount == 0) {
                        reasons.add("✘ Vague commit messages detected (" + weakCommitCount + " commits)");
                }

                DimensionResult.Builder builder = DimensionResult.builder(status)
                                .score(score)
                                .confidence(confidence)
                                .totalCandidateItemCount(commits.size())
                                .analyzedItemCount(totalCommits)
                                .failedItemCount(0)
                                .issues(commitIssues)
                                .detail("totalCommitsAnalyzed", totalCommits)
                                .detail("goodCommitCount", goodCommitCount)
                                .detail("weakCommitCount", weakCommitCount)
                                .detail("genericCommitCount", genericCommitCount)
                                .detail("mergeCommitCount", mergeCommitCount)
                                .detail("conventionalCommitCount", conventionalCommitCount)
                                .detail("repeatedMessageCount", repeatedMessageCount)
                                .detail("averageMessageLength", roundTwoDecimals(averageMessageLength))
                                .detail("genericCommitMessages", genericCommitMessages)
                                .detail("weakCommitMessages", weakCommitMessages)
                                .detail("commitIssues", commitIssues)
                                .detail("reasons", reasons)
                                .detail("scopeNote", "Analysis covers the most recent 100 commits.");

                for (String ev : evidence) {
                        builder.evidence(ev);
                }

                if (status == AnalysisStatus.PARTIAL) {
                        builder.statusReason("Only " + nonMergeCount
                                        + " non-merge commits available for analysis (minimum 5 required for full confidence)");
                }

                return builder.build();
        }

        private CommitType classifyCommit(String message) {
                String normalized = normalize(message);

                if (MERGE_PATTERN.matcher(message).matches()) {
                        return CommitType.MERGE;
                }

                if (GENERIC_MESSAGES.contains(normalized)) {
                        return CommitType.GENERIC;
                }

                if (VERSION_ONLY_PATTERN.matcher(normalized).matches()) {
                        return CommitType.GENERIC;
                }

                if (normalized.length() < 8) {
                        return CommitType.GENERIC;
                }

                if (containsVaguePhrase(normalized)) {
                        return CommitType.WEAK;
                }

                if (CONVENTIONAL_COMMIT_PATTERN.matcher(message).matches()) {
                        return CommitType.GOOD;
                }

                if (normalized.length() < 15) {
                        return CommitType.WEAK;
                }

                if (containsActionVerb(normalized)) {
                        return CommitType.GOOD;
                }

                return CommitType.WEAK;
        }

        private boolean containsVaguePhrase(String normalizedMessage) {
                for (String vaguePhrase : VAGUE_PHRASES) {
                        if (normalizedMessage.contains(vaguePhrase)) {
                                return true;
                        }
                }
                return false;
        }

        private boolean containsActionVerb(String normalizedMessage) {
                String cleanedMessage = normalizedMessage.replaceAll("[^a-z0-9\\s]", " ");
                String[] words = cleanedMessage.trim().split("\\s+");
                int wordsToInspect = Math.min(words.length, 5);
                for (int i = 0; i < wordsToInspect; i++) {
                        if (ACTION_VERBS.contains(words[i])) {
                                return true;
                        }
                }
                return false;
        }

        private int calculateRepeatedMessageCount(Map<String, Integer> messageFrequency, List<String> commitIssues) {
                int repeatedMessageCount = 0;
                for (Map.Entry<String, Integer> entry : messageFrequency.entrySet()) {
                        int frequency = entry.getValue();
                        if (frequency <= 1) continue;
                        repeatedMessageCount += frequency - 1;
                        commitIssues.add("Repeated commit message: " + entry.getKey() + " (" + frequency + " times)");
                }
                return repeatedMessageCount;
        }

        private int calculateScore(
                        int totalCommits,
                        int goodCommitCount,
                        int weakCommitCount,
                        int genericCommitCount,
                        int mergeCommitCount,
                        int conventionalCommitCount,
                        int repeatedMessageCount,
                        String repositoryType) {

                int qualityRelevantCommits = totalCommits - mergeCommitCount;

                if (qualityRelevantCommits <= 0) {
                        return 0;
                }

                boolean isSmallOrStudent = "PORTFOLIO".equals(repositoryType) || "README_ONLY".equals(repositoryType) 
                    || "HTML_CSS".equals(repositoryType) || "DATASET_REPOSITORY".equals(repositoryType)
                    || "POWER_BI".equals(repositoryType) || qualityRelevantCommits < 10;
                
                // Soften penalties for repositories with very few commits or non-software repos
                double baseScore = isSmallOrStudent ? 60.0 : 0.0;
                
                double weightedQuality = goodCommitCount + (weakCommitCount * 0.40);
                double qualityRatio = weightedQuality / qualityRelevantCommits;

                int maxPossibleAdditional = 100 - (int)baseScore - 10;
                int score = (int) Math.round(baseScore + (qualityRatio * maxPossibleAdditional));

                double conventionalRatio = (double) conventionalCommitCount / qualityRelevantCommits;
                
                // If it's a small project, don't penalize much for missing conventional commits
                if (conventionalRatio > 0) {
                    score += (int) Math.round(conventionalRatio * 10);
                } else if (isSmallOrStudent) {
                    score += 10;
                }

                if (qualityRelevantCommits >= 5) {
                        score += 5;
                }

                score -= Math.min(repeatedMessageCount * 3, 15);

                int finalScore = Math.max(0, Math.min(score, 100));
                
                // Explicit caps based on sample size to prevent score inflation
                if (qualityRelevantCommits < 5) {
                    finalScore = Math.min(finalScore, 65);
                } else if (qualityRelevantCommits < 10) {
                    finalScore = Math.min(finalScore, 75);
                }
                
                return finalScore;
        }

        private String extractCommitMessage(Map<String, Object> commitData) {
                if (commitData == null) return null;
                Object commitObject = commitData.get("commit");
                if (!(commitObject instanceof Map<?, ?> commitMap)) return null;
                Object message = commitMap.get("message");
                if (message == null) return null;
                return message.toString();
        }

        private String extractFirstLine(String message) {
                return message.lines().findFirst().orElse("").trim();
        }

        private String normalize(String message) {
                return message.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
        }

        private double roundTwoDecimals(double value) {
                return Math.round(value * 100.0) / 100.0;
        }

        private enum CommitType {
                GOOD, WEAK, GENERIC, MERGE
        }
}