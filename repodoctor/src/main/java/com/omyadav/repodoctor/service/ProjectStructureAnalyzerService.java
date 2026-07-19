package com.omyadav.repodoctor.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.omyadav.repodoctor.analysis.AnalysisStatus;
import com.omyadav.repodoctor.analysis.DimensionResult;

@Service
public class ProjectStructureAnalyzerService {

    public DimensionResult analyzeStructure(Map<String, Object> repositoryTree, String repositoryType) {
        if (repositoryTree == null) {
            return DimensionResult.fetchFailed("Repository tree is null");
        }

        Object treeValue = repositoryTree.get("tree");
        if (!(treeValue instanceof List<?>)) {
            return DimensionResult.fetchFailed("Repository tree is malformed or unavailable");
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tree = (List<Map<String, Object>>) treeValue;

        if (tree.isEmpty()) {
            return DimensionResult.notAnalyzable("Repository tree is empty");
        }

        int scorePresence = 0;
        int scoreCompleteness = 0;
        int scoreQuality = 0;

        List<String> evidence = new ArrayList<>();
        Map<String, Object> details = new HashMap<>();
        List<String> reasons = new ArrayList<>();
        
        long srcFiles = countFiles(tree, "src/");
        long testFiles = countFiles(tree, "test/") + countFiles(tree, "tests/") + countFiles(tree, "spec/");
        long libFiles = countFiles(tree, "lib/");
        long docsFiles = countFiles(tree, "docs/");
        long configFiles = countFiles(tree, "config/") + countFiles(tree, ".github/");
        long publicFiles = countFiles(tree, "public/") + countFiles(tree, "assets/") + countFiles(tree, "resources/");

        boolean hasSrcDir = srcFiles > 0;
        boolean hasTestDir = testFiles > 0;
        boolean hasLibDir = libFiles > 0;
        
        // Presence
        if (hasSrcDir || hasLibDir) {
            scorePresence += 15;
            evidence.add("Source code directory detected.");
            reasons.add("✔ Source directory detected");
        } else {
            reasons.add("✘ No dedicated source directory found");
        }
        
        if (hasTestDir) {
            scorePresence += 10;
            evidence.add("Test directory detected.");
            reasons.add("✔ Test directory detected");
        } else {
            reasons.add("✘ No test directory found");
        }
        
        if (configFiles > 0) {
            scorePresence += 5;
            evidence.add("Configuration isolated in dedicated directories.");
            reasons.add("✔ Config/GitHub directory isolated");
        }
        
        if (docsFiles > 0) {
            scorePresence += 5;
        }
        
        // Completeness & Quality depending on Type
        if ("SPRING_BOOT".equals(repositoryType) || "JAVA".equals(repositoryType)) {
            if (countFiles(tree, "src/main/java/") > 0) {
                scoreCompleteness += 15;
                evidence.add("Standard Java/Spring Boot layout (src/main/java) followed.");
                reasons.add("✔ Standard Spring Boot source structure (src/main/java)");
            } else {
                reasons.add("✘ Missing standard Java source structure");
            }
            if (countFiles(tree, "src/test/java/") > 0) {
                scoreCompleteness += 15;
                evidence.add("Standard Java/Spring Boot test layout (src/test/java) followed.");
                reasons.add("✔ Standard Spring Boot test structure (src/test/java)");
            }
            
            // Quality - are there nested packages?
            long nestedJava = tree.stream()
                .filter(item -> isBlob(item))
                .map(item -> item.get("path").toString())
                .filter(p -> p.contains("src/main/java/com/") || p.contains("src/main/java/org/"))
                .count();
                
            if (nestedJava > 0) {
                scoreQuality += 35;
                evidence.add("Proper package nesting (com/org) detected.");
                reasons.add("✔ Professional package nesting detected");
            } else {
                reasons.add("✘ Flat package hierarchy");
            }
            
        } else if ("REACT".equals(repositoryType) || "VUE".equals(repositoryType) || "ANGULAR".equals(repositoryType) || "NODE".equals(repositoryType)) {
            if (hasSrcDir) {
                scoreCompleteness += 15;
            }
            if (publicFiles > 0) {
                scoreCompleteness += 15;
                evidence.add("Static assets/public directory isolated.");
                reasons.add("✔ Public/assets directory isolated");
            }
            
            // Quality - module separation
            long components = countFiles(tree, "src/components/") + countFiles(tree, "src/utils/") + countFiles(tree, "src/services/");
            if (components > 0) {
                scoreQuality += 35;
                evidence.add("Logical module separation detected in frontend structure.");
                reasons.add("✔ Clear module separation (components, utils, etc)");
            } else {
                reasons.add("✘ Flat component hierarchy");
            }
        } else if ("PYTHON".equals(repositoryType) || "DJANGO".equals(repositoryType)) {
            if (hasSrcDir || hasLibDir || hasTestDir) {
                scoreCompleteness += 15;
            }
            long initPyCount = countFilesEndsWith(tree, "__init__.py");
            if (initPyCount > 0) {
                scoreCompleteness += 15;
                evidence.add("Python module structure (__init__.py) followed.");
                reasons.add("✔ Standard Python module structure");
            }
            if (initPyCount >= 3) {
                scoreQuality += 35;
                evidence.add("Good Python package nesting detected.");
                reasons.add("✔ Nested Python packages");
            }
        } else if ("JUPYTER_NOTEBOOK".equals(repositoryType) || "DOCUMENTATION_REPOSITORY".equals(repositoryType)) {
            // simpler expectations
            scoreCompleteness += 20;
            scoreQuality += 30; // Implicitly okay if files are logically grouped
            evidence.add("Flat or simple structure acceptable for Notebooks/Docs.");
            reasons.add("✔ Structure is appropriate for Notebooks/Docs");
            if (docsFiles > 0 || srcFiles > 0) {
                scoreCompleteness += 10;
                scoreQuality += 5;
            }
        } else if ("README_ONLY".equals(repositoryType) || "EMPTY".equals(repositoryType)) {
            scorePresence = 0;
            scoreCompleteness = 0;
            scoreQuality = 0;
            evidence.add("No project structure expected or present for a single-file or empty repository.");
            reasons.add("✔ No structure needed for empty/README-only repo");
        } else {
            // General / Unknown
            if (hasSrcDir || hasLibDir) scoreCompleteness += 15;
            if (hasTestDir) scoreCompleteness += 15;
            if (srcFiles > 5) scoreQuality += 20;
            if (configFiles > 0) scoreQuality += 15;
        }

        // Cap scores
        scorePresence = Math.min(30, scorePresence);
        scoreCompleteness = Math.min(35, scoreCompleteness);
        scoreQuality = Math.min(35, scoreQuality);

        int totalScore = scorePresence + scoreCompleteness + scoreQuality;

        // Failsafe: if there's hardly any structure and it's not a doc repo
        long totalBlobs = tree.stream().filter(this::isBlob).count();
        if (totalBlobs < 5 && totalScore > 50 && !"DOCUMENTATION_REPOSITORY".equals(repositoryType)) {
            totalScore = 20;
            evidence.add("Score reduced due to severe lack of actual files.");
            reasons.add("✘ Severe lack of source files reduces structural score");
        }

        boolean truncated = Boolean.TRUE.equals(repositoryTree.get("truncated"));
        AnalysisStatus status = truncated ? AnalysisStatus.PARTIAL : AnalysisStatus.SUCCESS;

        Map<String, Integer> breakdown = new HashMap<>();
        breakdown.put("Presence", scorePresence);
        breakdown.put("Completeness", scoreCompleteness);
        breakdown.put("Quality", scoreQuality);
        details.put("Score Breakdown", breakdown);
        
        // Find root clutter
        List<String> rootClutter = new ArrayList<>();
        Map<String, List<String>> filenameToPaths = new HashMap<>();
        List<String> suspiciousNamedFiles = new ArrayList<>();
        
        for (Map<String, Object> item : tree) {
            if (!isBlob(item)) continue;
            String path = item.get("path").toString();
            String lowerPath = path.toLowerCase(Locale.ROOT);
            String[] parts = path.split("/");
            String filename = parts[parts.length - 1];
            String lowerFilename = filename.toLowerCase(Locale.ROOT);
            
            // Root clutter
            if (parts.length == 1) {
                if (!lowerPath.equals("readme.md") && !lowerPath.equals("readme") 
                    && !lowerPath.equals(".gitignore") && !lowerPath.equals("license")
                    && !lowerPath.equals("pom.xml") && !lowerPath.equals("package.json")
                    && !lowerPath.equals("build.gradle") && !lowerPath.equals("settings.gradle")
                    && !lowerPath.equals("gradlew") && !lowerPath.equals("gradlew.bat")
                    && !lowerPath.equals("mvnw") && !lowerPath.equals("mvnw.cmd")
                    && !lowerPath.startsWith(".git") && !lowerPath.endsWith(".yml") 
                    && !lowerPath.endsWith(".yaml") && !lowerPath.endsWith(".toml")
                    && !lowerPath.equals("requirements.txt") && !lowerPath.equals("setup.py")
                    && !lowerPath.equals("manage.py") && !lowerPath.equals("dockerfile")
                    && !lowerPath.equals("angular.json") && !lowerPath.equals("tsconfig.json")
                    && !lowerPath.equals("vite.config.js") && !lowerPath.equals("vite.config.ts")
                    && !lowerPath.equals("webpack.config.js") && !lowerPath.endsWith(".pbix")
                    && !lowerPath.endsWith(".ipynb") && !lowerPath.endsWith(".pkl")
                    && !lowerPath.endsWith(".csv") && !lowerPath.endsWith(".jsonl")) {
                    rootClutter.add(path);
                }
            }
            
            // Duplicates
            filenameToPaths.computeIfAbsent(lowerFilename, k -> new ArrayList<>()).add(path);
            
            // Suspicious names
            if (lowerFilename.contains("v2") || lowerFilename.contains("final") 
                || lowerFilename.contains("copy") || lowerFilename.contains("backup")) {
                // Ignore safe terms like 'copy' inside package names, focus on exact words
                if (lowerFilename.matches(".*\\b(v2|final|copy|backup)\\b.*") && !lowerFilename.endsWith(".txt")) {
                    suspiciousNamedFiles.add(path);
                }
            }
        }
        
        Map<String, List<String>> duplicateLookingFiles = new HashMap<>();
        int duplicateLookingFileCount = 0;
        for (Map.Entry<String, List<String>> entry : filenameToPaths.entrySet()) {
            if (entry.getValue().size() > 1 && !entry.getKey().equals("index.js") 
                && !entry.getKey().equals("index.ts") && !entry.getKey().equals("index.html")
                && !entry.getKey().equals("__init__.py") && !entry.getKey().equals("readme.md")
                && !entry.getKey().equals(".gitignore") && !entry.getKey().equals("pom.xml")
                && !entry.getKey().equals("package.json") && !entry.getKey().equals("build.gradle")
                && !entry.getKey().equals("application.properties") && !entry.getKey().equals("application.yml")
                && !entry.getKey().equals("messages.properties") && !entry.getKey().equals("setup.py")
                && !entry.getKey().equals("requirements.txt") && !entry.getKey().equals("dockerfile")
                && !entry.getKey().endsWith(".csv") && !entry.getKey().endsWith(".ipynb")) {
                duplicateLookingFiles.put(entry.getKey(), entry.getValue());
                duplicateLookingFileCount += entry.getValue().size();
            }
        }

        if (rootClutter.size() > 0) {
            reasons.add("✘ Root directory is cluttered (" + rootClutter.size() + " files)");
        }
        if (duplicateLookingFileCount > 0) {
            reasons.add("✘ Potential duplicate files detected (" + duplicateLookingFileCount + ")");
        }

        details.put("rootClutterCount", rootClutter.size());
        details.put("rootClutter", rootClutter);
        details.put("duplicateLookingFileCount", duplicateLookingFileCount);
        details.put("duplicateLookingFiles", duplicateLookingFiles);
        details.put("suspiciousNamedFileCount", suspiciousNamedFiles.size());
        details.put("suspiciousNamedFiles", suspiciousNamedFiles);
        details.put("reasons", reasons);

        DimensionResult.Builder builder = DimensionResult.builder(status)
                .score(totalScore)
                .confidence(truncated ? 0.7 : 1.0)
                .totalCandidateItemCount(tree.size())
                .analyzedItemCount(tree.size())
                .failedItemCount(0)
                .details(details);

        for (String ev : evidence) {
            builder.evidence(ev);
        }

        if (truncated) {
            builder.statusReason("Repository tree is truncated by GitHub API limit");
        }

        return builder.build();
    }

    private long countFiles(List<Map<String, Object>> tree, String prefix) {
        return tree.stream()
                .filter(this::isBlob)
                .map(item -> item.get("path").toString().toLowerCase(Locale.ROOT))
                .filter(p -> p.contains("/" + prefix) || p.startsWith(prefix))
                .count();
    }
    
    private long countFilesEndsWith(List<Map<String, Object>> tree, String suffix) {
        return tree.stream()
                .filter(this::isBlob)
                .map(item -> item.get("path").toString().toLowerCase(Locale.ROOT))
                .filter(p -> p.endsWith(suffix))
                .count();
    }

    private boolean isBlob(Map<String, Object> item) {
        return "blob".equals(item.get("type"));
    }
}