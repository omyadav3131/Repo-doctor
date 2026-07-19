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

    private final ExcludedPathService excludedPathService;

    public RepositoryHygieneService(ExcludedPathService excludedPathService) {
        this.excludedPathService = excludedPathService;
    }

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
        List<String> explicitDirtyFiles = new ArrayList<>();
        Map<String, Integer> bulkDirtyDirectories = new HashMap<>();

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

            // Dirty files detection
            String matchedVendorDir = excludedPathService.getMatchedVendorOrBuildPath(path);
            if (matchedVendorDir != null) {
                hasDirtyFiles = true;
                bulkDirtyDirectories.put(matchedVendorDir, bulkDirtyDirectories.getOrDefault(matchedVendorDir, 0) + 1);
            } else if (lowerPath.endsWith(".pyc") || 
                lowerPath.equals(".env") || lowerPath.endsWith(".class") || 
                lowerPath.endsWith(".log") || lowerPath.endsWith(".tmp") || 
                lowerPath.endsWith(".bak") || lowerPath.endsWith(".mv.db") || 
                lowerPath.endsWith(".trace.db") || lowerPath.endsWith(".h2.db")) {
                
                hasDirtyFiles = true;
                explicitDirtyFiles.add(path);
                issues.add("Dirty file committed: " + path);
            }
        }

        for (Map.Entry<String, Integer> entry : bulkDirtyDirectories.entrySet()) {
            String msg = entry.getKey() + "/ (" + entry.getValue() + " files committed)";
            explicitDirtyFiles.add(msg);
            issues.add("Bulk dirty directory committed: " + msg);
        }

        List<String> reasons = new ArrayList<>();

        // Base Maintenance
        if (hasReadme) {
            scorePresence += 15;
            evidence.add("README found.");
            reasons.add("✔ README detected");
        } else {
            issues.add("Missing README.");
            reasons.add("✘ Missing README");
        }
        
        if (hasGitignore) {
            scorePresence += 15;
            evidence.add(".gitignore found.");
            reasons.add("✔ .gitignore detected");
        } else {
            issues.add("Missing .gitignore.");
            reasons.add("✘ Missing .gitignore");
        }

        if (hasLicense) {
            scorePresence += 10;
            evidence.add("License found.");
            reasons.add("✔ License detected");
        }

        if (!hasDirtyFiles) {
            scoreQuality += 20;
            evidence.add("No dirty files or secrets (.env, pycache, etc.) committed.");
            reasons.add("✔ No dirty/generated files committed");
        } else {
            reasons.add("✘ Dirty/generated files committed (" + explicitDirtyFiles.size() + ")");
        }

        // Context-aware expectations
        if (isPersonal) {
            // For personal/portfolio, we don't expect CI/CD or templates
            scoreCompleteness += 20; // Automatically grant completeness if base is fine
            scoreQuality += 20;
            evidence.add("Portfolio/Personal repo logic applied. Advanced OSS hygiene not required.");
            reasons.add("✔ Adjusted expectations for personal/portfolio repo");
        } else {
            // Enterprise / standard
            if (hasActions) {
                scoreCompleteness += 10;
                evidence.add("CI/CD pipeline (e.g. GitHub Actions) detected.");
                reasons.add("✔ CI/CD pipeline detected");
            } else {
                reasons.add("✘ No CI/CD pipeline detected");
            }
            
            if (hasSecurity) {
                scoreCompleteness += 5;
                evidence.add("Security policy detected.");
                reasons.add("✔ SECURITY.md detected");
            } else {
                reasons.add("✘ No SECURITY.md detected");
            }
            
            if (hasTemplates) {
                scoreCompleteness += 10;
                evidence.add("Issue/PR templates detected.");
                reasons.add("✔ Issue/PR templates detected");
            }
            
            if (hasDependabot) {
                scoreQuality += 10;
                evidence.add("Dependabot / Dependency management detected.");
                reasons.add("✔ Dependency management (Dependabot) detected");
            }
            
            if (hasCodeowners) {
                scoreQuality += 10;
                evidence.add("CODEOWNERS detected.");
                reasons.add("✔ CODEOWNERS detected");
            }
        }

        int totalScore = Math.min(100, scorePresence + scoreCompleteness + scoreQuality);

        Map<String, Integer> breakdown = new HashMap<>();
        breakdown.put("Presence", scorePresence);
        breakdown.put("Completeness", scoreCompleteness);
        breakdown.put("Quality", scoreQuality);
        details.put("Score Breakdown", breakdown);
        details.put("reasons", reasons);
        details.put("dirtyFilesFound", hasDirtyFiles);
        details.put("dirtyFiles", explicitDirtyFiles);

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