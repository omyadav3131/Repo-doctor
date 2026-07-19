package com.omyadav.repodoctor.service;

import com.omyadav.repodoctor.analysis.AnalysisStatus;
import com.omyadav.repodoctor.analysis.DimensionResult;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ReadmeAnalyzerService {

    public DimensionResult analyzeReadme(@Nullable Map<String, Object> readmeData, String repositoryType) {
        if (readmeData == null) {
            return DimensionResult.notAnalyzable("No README file found in the repository");
        }

        String encodedContent = readmeData.get("content").toString();
        String cleanContent = encodedContent.replaceAll("\\s", "");
        String content = new String(Base64.getDecoder().decode(cleanContent), StandardCharsets.UTF_8);

        List<String> evidence = new ArrayList<>();
        Map<String, Object> details = new HashMap<>();
        List<String> reasons = new ArrayList<>();

        int scorePresence = 0;
        int scoreCompleteness = 0;
        int scoreQuality = 0;

        // Presence (Max 30)
        scorePresence += 15; // It exists
        if (content.length() > 50) {
            scorePresence += 15;
            evidence.add("README is present and contains some text.");
            reasons.add("✔ README is present and non-empty");
        } else {
            evidence.add("README is practically empty.");
            reasons.add("✘ README is practically empty");
        }

        if (content.length() > 50) {
            // Completeness (Max 40)
            boolean hasOverview = hasHeadingWithContent(content, "overview", "business problem", "about");
            boolean hasInstallation = hasHeadingWithContent(content, "install", "setup", "getting started");
            boolean hasUsage = hasHeadingWithContent(content, "usage", "run", "quickstart", "how to use");
            boolean hasConfig = hasHeadingWithContent(content, "env", "config", "environment");
            boolean hasArchitecture = hasHeadingWithContent(content, "architecture", "design", "structure", "repository structure");
            boolean hasFeatures = hasHeadingWithContent(content, "features");
            boolean hasScreenshots = hasHeadingWithContent(content, "screenshot", "dashboard", "ui");
            boolean hasResults = hasHeadingWithContent(content, "result", "model evaluation", "evaluation", "metrics", "performance");
            boolean hasContributing = hasHeadingWithContent(content, "contributing", "license", "author", "future work");
            boolean hasApiDocs = hasHeadingWithContent(content, "api", "docs", "documentation", "examples", "notebook guide");

            boolean expectScreenshots = false;
            boolean expectResults = false;
            
            if (repositoryType != null) {
                String type = repositoryType.toUpperCase(java.util.Locale.ROOT);
                if (type.equals("REACT") || type.equals("VUE") || type.equals("ANGULAR") 
                    || type.equals("FLUTTER") || type.equals("PORTFOLIO") || type.equals("HTML_CSS")) {
                    expectScreenshots = true;
                }
                if (type.equals("MACHINE_LEARNING") || type.equals("DATA_SCIENCE") 
                    || type.equals("JUPYTER_NOTEBOOK") || type.equals("DATASET_REPOSITORY")) {
                    expectResults = true;
                }
            }

            Map<String, Boolean> activeChecks = new LinkedHashMap<>();
            activeChecks.put("Overview / Business Problem", hasOverview);
            activeChecks.put("Installation instructions", hasInstallation);
            activeChecks.put("Usage examples", hasUsage);
            activeChecks.put("Configuration details", hasConfig);
            activeChecks.put("Architecture / Features", hasFeatures || hasArchitecture);
            activeChecks.put("Contributing / License", hasContributing);
            activeChecks.put("API / Documentation", hasApiDocs);
            
            if (expectScreenshots) {
                activeChecks.put("Screenshots / Dashboards", hasScreenshots);
            }
            if (expectResults) {
                activeChecks.put("Results / Model Evaluation", hasResults);
            }

            double scorePerCheck = 40.0 / activeChecks.size();
            for (Map.Entry<String, Boolean> entry : activeChecks.entrySet()) {
                if (entry.getValue()) {
                    scoreCompleteness += scorePerCheck;
                    evidence.add(entry.getKey() + " section found.");
                    reasons.add("✔ " + entry.getKey() + " documented");
                } else {
                    if (entry.getKey().equals("Installation instructions") || entry.getKey().equals("Usage examples")) {
                        reasons.add("✘ Missing " + entry.getKey().toLowerCase(java.util.Locale.ROOT));
                    }
                }
            }
            
            details.put("readmeChecks", activeChecks);

            // Quality (Max 30)
            if (content.length() >= 500) { scoreQuality += 5; }
            if (content.length() >= 1500) { scoreQuality += 5; reasons.add("✔ Extensive README documentation"); }

            if (hasCodeBlock(content)) { scoreQuality += 10; evidence.add("Code blocks detected."); reasons.add("✔ Useful code blocks/snippets included"); }
            if (hasMarkdownLink(content)) { scoreQuality += 5; }
            
            if (hasImageOrBadge(content)) { scoreQuality += 5; evidence.add("Images or badges detected."); reasons.add("✔ Images or badges used for presentation"); }

            if (isBoilerplate(content)) {
                scoreQuality -= 20;
                evidence.add("README appears to be mostly generated boilerplate.");
                reasons.add("✘ README appears to be unedited boilerplate");
            }
        }

        // Cap sub-scores
        scorePresence = Math.min(30, Math.max(0, scorePresence));
        scoreCompleteness = Math.min(40, Math.max(0, scoreCompleteness));
        scoreQuality = Math.min(30, Math.max(0, scoreQuality));

        int totalScore = scorePresence + scoreCompleteness + scoreQuality;

        Map<String, Integer> breakdown = new HashMap<>();
        breakdown.put("Presence", scorePresence);
        breakdown.put("Completeness", scoreCompleteness);
        breakdown.put("Quality", scoreQuality);
        details.put("Score Breakdown", breakdown);
        details.put("contentLength", content.length());
        details.put("reasons", reasons);

        DimensionResult.Builder builder = DimensionResult.builder(AnalysisStatus.SUCCESS)
                .score(totalScore)
                .confidence(1.0)
                .details(details);

        for (String ev : evidence) {
            builder.evidence(ev);
        }

        return builder.build();
    }

    private boolean hasCodeBlock(String content) {
        if (content.contains("```")) return true;
        String[] lines = content.split("\\R");
        int indentCount = 0;
        for (String line : lines) {
            if (line.startsWith("    ") && !line.trim().isEmpty()) {
                indentCount++;
                if (indentCount >= 2) return true;
            } else {
                indentCount = 0;
            }
        }
        return false;
    }

    private boolean hasHeadingWithContent(String content, String... headings) {
        String[] lines = content.split("\\R");
        boolean inTargetSection = false;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.matches("^#{1,6}\\s+.*")) {
                String headingText = trimmed.replaceFirst("^#{1,6}\\s+", "").toLowerCase();
                inTargetSection = false;
                for (String target : headings) {
                    if (headingText.contains(target.toLowerCase())) {
                        inTargetSection = true;
                        break;
                    }
                }
            } else if (inTargetSection && !trimmed.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private boolean hasMarkdownLink(String content) {
        return content.matches("(?s).*\\[.*?\\]\\(.*?\\).*");
    }

    private boolean hasImageOrBadge(String content) {
        return content.contains("![") || content.contains("<img");
    }

    private boolean isBoilerplate(String content) {
        int boilerplateMarkers = 0;
        String[] patterns = {
                "\\[project_name\\]", "\\[Your Project\\]", "<project_name>", "<your_name>",
                "<!-- TODO", "<!-- Add"
        };
        for (String pattern : patterns) {
            if (Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(content).find()) {
                boilerplateMarkers++;
            }
        }
        if (content.toLowerCase().contains("about the project") && content.toLowerCase().contains("built with")) {
            if (content.toLowerCase().indexOf("built with") > content.toLowerCase().indexOf("about the project")) {
                boilerplateMarkers++;
            }
        }
        return boilerplateMarkers >= 2;
    }
}