package com.omyadav.repodoctor.service;

import com.omyadav.repodoctor.analysis.AnalysisStatus;
import com.omyadav.repodoctor.analysis.DimensionResult;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class RepositoryHygieneService {

    public DimensionResult analyzeHygiene(Map<String, Object> repositoryTree, String repositoryType) {

        Object treeObj = repositoryTree.get("tree");
        if (!(treeObj instanceof List<?>)) {
            return DimensionResult.fetchFailed("Repository tree is malformed or unavailable");
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tree = (List<Map<String, Object>>) treeObj;

        if (tree.isEmpty()) {
            return DimensionResult.notAnalyzable("Repository tree is empty");
        }

        boolean truncated = Boolean.TRUE.equals(repositoryTree.get("truncated"));
        AnalysisStatus status = truncated ? AnalysisStatus.PARTIAL : AnalysisStatus.SUCCESS;
        double confidence = truncated ? 0.7 : 1.0;

        List<String> issues = new ArrayList<>();
        List<String> evidence = new ArrayList<>();
        Map<String, Object> details = new HashMap<>();

        boolean isPersonal = "PORTFOLIO".equals(repositoryType) || "HTML_CSS".equals(repositoryType) || "JUPYTER_NOTEBOOK".equals(repositoryType);
        
        int scorePresence = 0;
        int scoreCompleteness = 0;
        int scoreQuality = 0;

        boolean hasReadme = false;
        boolean hasGitignore = false;
        boolean hasLicense = false;
        boolean hasActions = false;
        boolean hasSecurity = false;
        boolean hasTemplates = false;
        boolean hasDependabot = false;
        boolean hasCodeowners = false;

        boolean hasDirtyFiles = false;

        for (Map<String, Object> item : tree) {
            String path = item.get("path").toString();
            String lowerPath = path.toLowerCase(Locale.ROOT);

            if (lowerPath.equals("readme.md") || lowerPath.equals("readme")) hasReadme = true;
            if (lowerPath.equals(".gitignore")) hasGitignore = true;
            if (lowerPath.equals("license") || lowerPath.equals("license.md")) hasLicense = true;
            
            if (lowerPath.startsWith(".github/workflows/") || lowerPath.equals(".travis.yml")) hasActions = true;
            if (lowerPath.equals("security.md") || lowerPath.equals(".github/security.md")) hasSecurity = true;
            if (lowerPath.contains("issue_template") || lowerPath.contains("pull_request_template")) hasTemplates = true;
            if (lowerPath.equals(".github/dependabot.yml")) hasDependabot = true;
            if (lowerPath.equals("codeowners") || lowerPath.equals(".github/codeowners")) hasCodeowners = true;

            // Dirty files
            if (lowerPath.startsWith(".vs/") || lowerPath.contains(".ipynb_checkpoints") || lowerPath.contains("__pycache__") || lowerPath.endsWith(".pyc") || lowerPath.equals(".env")) {
                hasDirtyFiles = true;
                issues.add("Dirty file committed: " + path);
            }
        }

        // Base Maintenance
        if (hasReadme) {
            scorePresence += 15;
            evidence.add("README found.");
        } else {
            issues.add("Missing README.");
        }
        
        if (hasGitignore) {
            scorePresence += 15;
            evidence.add(".gitignore found.");
        } else {
            issues.add("Missing .gitignore.");
        }

        if (hasLicense) {
            scorePresence += 10;
            evidence.add("License found.");
        }

        if (!hasDirtyFiles) {
            scoreQuality += 20;
            evidence.add("No dirty files or secrets (.env, pycache, etc.) committed.");
        }

        // Context-aware expectations
        if (isPersonal) {
            // For personal/portfolio, we don't expect CI/CD or templates
            scoreCompleteness += 20; // Automatically grant completeness if base is fine
            scoreQuality += 20;
            evidence.add("Portfolio/Personal repo logic applied. Advanced OSS hygiene not required.");
        } else {
            // Enterprise / standard
            if (hasActions) {
                scoreCompleteness += 10;
                evidence.add("CI/CD pipeline (e.g. GitHub Actions) detected.");
            }
            if (hasSecurity) {
                scoreCompleteness += 5;
                evidence.add("Security policy detected.");
            }
            if (hasTemplates) {
                scoreCompleteness += 10;
                evidence.add("Issue/PR templates detected.");
            }
            if (hasDependabot) {
                scoreQuality += 10;
                evidence.add("Dependabot / Dependency management detected.");
            }
            if (hasCodeowners) {
                scoreQuality += 10;
                evidence.add("CODEOWNERS detected.");
            }
        }

        int totalScore = Math.min(100, scorePresence + scoreCompleteness + scoreQuality);

        Map<String, Integer> breakdown = new HashMap<>();
        breakdown.put("Presence", scorePresence);
        breakdown.put("Completeness", scoreCompleteness);
        breakdown.put("Quality", scoreQuality);
        details.put("Score Breakdown", breakdown);

        DimensionResult.Builder builder = DimensionResult.builder(status)
                .score(totalScore)
                .confidence(confidence)
                .totalCandidateItemCount(tree.size())
                .analyzedItemCount(tree.size())
                .failedItemCount(0)
                .issues(issues)
                .details(details);

        for (String ev : evidence) {
            builder.evidence(ev);
        }

        if (truncated) {
            builder.statusReason("Repository tree is truncated by GitHub API limit");
        }

        return builder.build();
    }
}