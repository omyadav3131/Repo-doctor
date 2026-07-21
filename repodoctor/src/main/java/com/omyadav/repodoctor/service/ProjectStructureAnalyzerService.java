package com.omyadav.repodoctor.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.omyadav.repodoctor.analysis.AnalysisStatus;
import com.omyadav.repodoctor.analysis.DimensionResult;

@Service
public class ProjectStructureAnalyzerService {

    private static final List<String> COMMON_SRC_DIRS = List.of(
        "src/", "app/", "backend/", "backend/app/", "server/", "api/", "client/", "frontend/"
    );

    private final ExcludedPathService excludedPathService;
    private final RepositoryContentService repositoryContentService;

    public ProjectStructureAnalyzerService(ExcludedPathService excludedPathService, RepositoryContentService repositoryContentService) {
        this.excludedPathService = excludedPathService;
        this.repositoryContentService = repositoryContentService;
    }

    public DimensionResult analyzeStructure(String owner, String repository, String branch, Map<String, Object> repositoryTree, String repositoryType) {
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
        
        long srcFiles = 0;
        for (String dir : COMMON_SRC_DIRS) {
            srcFiles += countFiles(tree, dir);
        }
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
            if (countFiles(tree, "src/main/java/") > 0 || countFiles(tree, "backend/src/main/java/") > 0 || countFiles(tree, "app/src/main/java/") > 0 || countFiles(tree, "server/src/main/java/") > 0) {
                scoreCompleteness += 15;
                evidence.add("Standard Java/Spring Boot layout (src/main/java) followed.");
                reasons.add("✔ Standard Spring Boot source structure");
            } else {
                reasons.add("✘ Missing standard Java source structure");
            }
            if (countFiles(tree, "src/test/java/") > 0 || countFiles(tree, "backend/src/test/java/") > 0 || countFiles(tree, "app/src/test/java/") > 0 || countFiles(tree, "server/src/test/java/") > 0) {
                scoreCompleteness += 15;
                evidence.add("Standard Java/Spring Boot test layout (src/test/java) followed.");
                reasons.add("✔ Standard Spring Boot test structure");
            }
            
            // Quality - are there nested packages?
            long nestedJava = tree.stream()
                .filter(item -> isBlob(item))
                .map(item -> item.get("path").toString())
                .filter(p -> p.contains("src/main/java/com/") || p.contains("src/main/java/org/")
                          || p.contains("backend/src/main/java/com/") || p.contains("backend/src/main/java/org/")
                          || p.contains("app/src/main/java/com/") || p.contains("app/src/main/java/org/")
                          || p.contains("server/src/main/java/com/") || p.contains("server/src/main/java/org/"))
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
            long components = countFiles(tree, "src/components/") + countFiles(tree, "src/utils/") + countFiles(tree, "src/services/")
                            + countFiles(tree, "app/components/") + countFiles(tree, "client/src/components/") + countFiles(tree, "frontend/src/components/");
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
            long appPyCount = countFilesEndsWith(tree, "routes.py") + countFilesEndsWith(tree, "models.py") + countFilesEndsWith(tree, "views.py") + countFilesEndsWith(tree, "main.py");
            if (initPyCount > 0 || appPyCount > 0) {
                scoreCompleteness += 15;
                evidence.add("Python module structure or organized app files followed.");
                reasons.add("✔ Standard Python module/app structure");
            }
            if (initPyCount >= 3 || appPyCount >= 2) {
                scoreQuality += 35;
                evidence.add("Good Python package nesting or app organization detected.");
                reasons.add("✔ Organized Python packages/app");
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
        } else if ("MACHINE_LEARNING".equals(repositoryType) || "DATA_SCIENCE".equals(repositoryType) || "DATASET_REPOSITORY".equals(repositoryType)) {
            long dataSources = countFiles(tree, "notebooks/") + countFiles(tree, "data/") + countFiles(tree, "models/") + countFiles(tree, "scripts/") + srcFiles;
            if (dataSources > 0) {
                scoreCompleteness += 20;
                evidence.add("Data Science structure (notebooks/data/models/scripts) detected.");
                reasons.add("✔ Standard Data Science folder organization");
            }
            long notebooksCount = countFilesEndsWith(tree, ".ipynb");
            if (notebooksCount > 2) {
                scoreQuality += 10;
                evidence.add("Organized notebooks found.");
                reasons.add("✔ Good notebook organization");
            }
            if (countFilesEndsWith(tree, "requirements.txt") > 0 || countFilesEndsWith(tree, "environment.yml") > 0) {
                scoreCompleteness += 10;
                scoreQuality += 10;
                evidence.add("Environment config present.");
                reasons.add("✔ Environment configuration present");
            }
            scoreQuality += 15; // implicitly okay if basic elements met
        } else if ("POWER_BI".equals(repositoryType)) {
            long pbixCount = countFilesEndsWith(tree, ".pbix");
            if (pbixCount > 0) {
                scorePresence += 15; // compensate for no src/
                scoreCompleteness += 20;
                scoreQuality += 35;
                evidence.add("PowerBI repository structure acceptable (PBIX files present).");
                reasons.add("✔ Valid PowerBI repository structure");
            }
        } else if ("FLUTTER".equals(repositoryType)) {
            if (libFiles > 0) scoreCompleteness += 15;
            if (testFiles > 0) scoreCompleteness += 15;
            
            long modules = countFiles(tree, "lib/screens/") + countFiles(tree, "lib/models/") + countFiles(tree, "lib/widgets/");
            if (modules > 0) {
                scoreQuality += 35;
                evidence.add("Logical module separation detected in Flutter structure.");
                reasons.add("✔ Clear module separation (screens, widgets, models)");
            } else {
                reasons.add("✘ Flat lib directory hierarchy");
            }
        } else if ("CPLUSPLUS".equals(repositoryType)) {
            if (srcFiles > 0) scoreCompleteness += 15;
            if (countFiles(tree, "include/") > 0) {
                scoreCompleteness += 15;
                evidence.add("C++ include directory detected.");
                reasons.add("✔ C++ header separation (include/)");
            }
            if (countFilesEndsWith(tree, "cmakelists.txt") > 0 || countFilesEndsWith(tree, "makefile") > 0) {
                scoreQuality += 35;
                evidence.add("C++ build configuration (CMake/Makefile) present.");
                reasons.add("✔ Standard C++ build config present");
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
        List<String> suspiciousCandidates = new ArrayList<>();
        Set<String> allPaths = new HashSet<>();
        
        for (Map<String, Object> item : tree) {
            if (!isBlob(item)) continue;
            String path = item.get("path").toString();
            allPaths.add(path);
            
            if (excludedPathService.isVendorOrBuildPath(path)) {
                continue;
            }
            
            String lowerPath = path.toLowerCase(Locale.ROOT);
            String[] parts = path.split("/");
            String filename = parts[parts.length - 1];
            String lowerFilename = filename.toLowerCase(Locale.ROOT);
            
            // Extract basename for suspicious checks (remove extension)
            String baseName = lowerFilename;
            int lastDot = lowerFilename.lastIndexOf('.');
            if (lastDot > 0) {
                baseName = lowerFilename.substring(0, lastDot);
            }
            
            // Root clutter
            if (parts.length == 1) {
                if (isRootClutter(lowerPath)) {
                    rootClutter.add(path);
                }
            }
            
            // Duplicates
            filenameToPaths.computeIfAbsent(lowerFilename, k -> new ArrayList<>()).add(path);
            
            // Suspicious names (must act as suffix on the base name)
            if (baseName.matches(".*[-_ ](v2|final|copy|backup)$")) {
                suspiciousCandidates.add(path);
            }
        }
        
        // Resolve Suspicious Named Files
        List<String> suspiciousNamedFiles = new ArrayList<>();
        for (String sus : suspiciousCandidates) {
            String dir = sus.contains("/") ? sus.substring(0, sus.lastIndexOf('/') + 1) : "";
            String filename = sus.substring(sus.lastIndexOf('/') + 1);
            String baseFilename = filename.replaceAll("(?i)[-_ ](v2|final|copy|backup)(\\.[a-z0-9]+)?$", "$2");
            String basePath = dir + baseFilename;
            
            if (allPaths.contains(basePath)) {
                String c1 = repositoryContentService.getRawFileContent(owner, repository, branch, sus);
                String c2 = repositoryContentService.getRawFileContent(owner, repository, branch, basePath);
                if (c1 != null && c2 != null && c1.trim().equals(c2.trim())) {
                    suspiciousNamedFiles.add(sus);
                }
            }
        }
        
        Map<String, List<String>> duplicateLookingFiles = new HashMap<>();
        int duplicateLookingFileCount = 0;
        for (Map.Entry<String, List<String>> entry : filenameToPaths.entrySet()) {
            if (entry.getValue().size() > 1) {
                List<String> actualDuplicates = filterActualDuplicates(owner, repository, branch, entry.getValue());
                if (actualDuplicates.size() > 1) {
                    duplicateLookingFiles.put(entry.getKey(), actualDuplicates);
                    duplicateLookingFileCount += actualDuplicates.size();
                }
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
                .confidence(truncated ? 0.6 : 1.0)
                .confidenceReason(truncated ? "Repository tree was truncated by GitHub API (100k+ limit)" : "Repository is fully analyzed")
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

    private boolean isRootClutter(String lowerPath) {
        // Raw source code in root is typically clutter (unless specific exceptions like setup.py)
        if (lowerPath.matches(".*\\.(java|py|cpp|c|h|cs|go|rs|php|rb|swift|kt|dart)$")) {
            return !(lowerPath.equals("setup.py") || lowerPath.equals("manage.py") || lowerPath.equals("main.go") || lowerPath.equals("build.rs"));
        }
        // Everything else (configs, docs, scripts, json, etc) is considered safe structural elements
        return false;
    }

    private List<String> filterActualDuplicates(String owner, String repository, String branch, List<String> paths) {
        List<String> flagged = new ArrayList<>();
        boolean[] isDuplicate = new boolean[paths.size()];
        
        for (int i = 0; i < paths.size(); i++) {
            for (int j = i + 1; j < paths.size(); j++) {
                if (isActualDuplicate(owner, repository, branch, paths.get(i), paths.get(j))) {
                    isDuplicate[i] = true;
                    isDuplicate[j] = true;
                }
            }
        }
        
        for (int i = 0; i < paths.size(); i++) {
            if (isDuplicate[i]) {
                flagged.add(paths.get(i));
            }
        }
        return flagged;
    }

    private boolean isActualDuplicate(String owner, String repository, String branch, String p1, String p2) {
        String[] parts1 = p1.toLowerCase(Locale.ROOT).split("/");
        String[] parts2 = p2.toLowerCase(Locale.ROOT).split("/");
        
        // 1. Generic Scaffolding/Topology Divergence Check
        // If they differ at the very first directory (e.g. ios/ vs macos/, frontend/ vs backend/)
        if (parts1.length > 1 && parts2.length > 1) {
            if (!parts1[0].equals(parts2[0])) {
                return false;
            }
        }
        
        // Monorepo specific divergence (packages/A vs packages/B)
        if (isMonorepoDivergence(parts1, parts2)) {
            return false;
        }

        // 2. Content Similarity Check (Evidence-based)
        String c1 = repositoryContentService.getRawFileContent(owner, repository, branch, p1);
        String c2 = repositoryContentService.getRawFileContent(owner, repository, branch, p2);
        
        if (c1 == null || c2 == null || c1.isBlank() || c2.isBlank()) {
            return false;
        }
        
        // If content is strictly identical or very close
        return c1.trim().equals(c2.trim());
    }

    private boolean isMonorepoDivergence(String[] parts1, String[] parts2) {
        int idx1 = -1;
        int idx2 = -1;
        for (int i = 0; i < parts1.length - 2; i++) {
            if (parts1[i].equals("packages") || parts1[i].equals("apps") || parts1[i].equals("modules")) {
                idx1 = i; break;
            }
        }
        for (int i = 0; i < parts2.length - 2; i++) {
            if (parts2[i].equals("packages") || parts2[i].equals("apps") || parts2[i].equals("modules")) {
                idx2 = i; break;
            }
        }
        if (idx1 != -1 && idx1 == idx2) {
            boolean prefixMatch = true;
            for(int i = 0; i < idx1; i++) {
                if(!parts1[i].equals(parts2[i])) prefixMatch = false;
            }
            if (prefixMatch && !parts1[idx1+1].equals(parts2[idx2+1])) {
                return true;
            }
        }
        return false;
    }
}