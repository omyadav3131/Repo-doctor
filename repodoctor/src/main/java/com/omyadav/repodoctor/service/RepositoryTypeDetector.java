package com.omyadav.repodoctor.service;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RepositoryTypeDetector {

    public String detectRepositoryType(String githubLanguage, List<String> allFiles) {
        if (allFiles == null || allFiles.isEmpty()) {
            return "EMPTY";
        }

        long notebookCount = countSuffix(allFiles, ".ipynb");
        long htmlCount = countSuffix(allFiles, ".html");
        long cssCount = countSuffix(allFiles, ".css");
        long markdownCount = countSuffix(allFiles, ".md");
        long javaCount = countSuffix(allFiles, ".java");
        long pythonCount = countSuffix(allFiles, ".py");
        long jsCount = countSuffix(allFiles, ".js");
        long tsCount = countSuffix(allFiles, ".ts");
        long totalFiles = allFiles.size();

        boolean hasPomXml = hasFile(allFiles, "pom.xml");
        boolean hasBuildGradle = hasFile(allFiles, "build.gradle");
        boolean hasPackageJson = hasFile(allFiles, "package.json");
        boolean hasRequirementsTxt = hasFile(allFiles, "requirements.txt");
        boolean hasManagePy = hasFile(allFiles, "manage.py");
        boolean hasReactMarkers = hasFile(allFiles, "src/App.jsx") || hasFile(allFiles, "src/App.js") || hasFile(allFiles, "src/index.js");
        boolean hasAngularMarkers = hasFile(allFiles, "angular.json");
        boolean hasVueMarkers = hasFile(allFiles, "src/main.js") && hasFile(allFiles, "src/App.vue");
        boolean hasFlutterMarkers = hasFile(allFiles, "pubspec.yaml");

        // Notebook and Data Science
        long pbixCount = countSuffix(allFiles, ".pbix");
        long modelCount = countSuffix(allFiles, ".pkl") + countSuffix(allFiles, ".h5") + countSuffix(allFiles, ".pt") + countSuffix(allFiles, ".onnx");
        long dataCount = countSuffix(allFiles, ".csv") + countSuffix(allFiles, ".parquet") + countSuffix(allFiles, ".jsonl");
        
        if (pbixCount > 0) {
            return "POWER_BI";
        }
        
        if (notebookCount > 0 && (notebookCount >= (totalFiles * 0.1) || modelCount > 0 || dataCount > 0)) {
            boolean hasML = hasMatch(allFiles, ".*(tensorflow|keras|pytorch|scikit-learn|xgboost|lightgbm|numpy|pandas).*")
                || hasMatch(allFiles, ".*(model|train|predict|inference).*\\.(py|ipynb)$");

            if (hasML) {
                return "MACHINE_LEARNING";
            }
            
            if (hasMatch(allFiles, ".*(data|dataset|csv|json).*") && !hasMatch(allFiles, ".*(src|app|main).*")) {
                return "DATASET_REPOSITORY";
            }

            if (githubLanguage != null && "Jupyter Notebook".equalsIgnoreCase(githubLanguage)) {
                return "JUPYTER_NOTEBOOK";
            }
        }
        
        if (modelCount > 0 || (dataCount > 0 && hasRequirementsTxt)) {
            return "DATA_SCIENCE";
        }
        
        if (dataCount >= (totalFiles * 0.5) && totalFiles > 1) {
            return "DATASET_REPOSITORY";
        }

        // Frameworks and Languages
        boolean isSpring = hasPomXml || hasBuildGradle;
        boolean isReact = hasPackageJson && hasReactMarkers;
        boolean isAngular = hasPackageJson && hasAngularMarkers;
        boolean isVue = hasPackageJson && hasVueMarkers;
        boolean isNode = hasPackageJson && !isReact && !isAngular && !isVue;
        boolean isDjango = (hasRequirementsTxt || hasManagePy) && hasManagePy;
        boolean isPython = (hasRequirementsTxt || hasManagePy) && !isDjango;
        boolean isFlutter = hasFlutterMarkers;
        
        long cppCount = countSuffix(allFiles, ".cpp") + countSuffix(allFiles, ".c") + countSuffix(allFiles, ".h") + countSuffix(allFiles, ".hpp");
        boolean isCpp = cppCount > 0 || hasFile(allFiles, "CMakeLists.txt");

        // Apply GitHub language primary tie-breaker
        if (githubLanguage != null) {
            if (isFlutter && "Dart".equalsIgnoreCase(githubLanguage)) return "FLUTTER";
            if (isSpring && "Java".equalsIgnoreCase(githubLanguage)) return "SPRING_BOOT";
            if ((isReact || isAngular || isVue || isNode) && ("JavaScript".equalsIgnoreCase(githubLanguage) || "TypeScript".equalsIgnoreCase(githubLanguage))) {
                if (isReact) return "REACT";
                if (isAngular) return "ANGULAR";
                if (isVue) return "VUE";
                return "NODE";
            }
            if (isDjango && "Python".equalsIgnoreCase(githubLanguage)) return "DJANGO";
            if (isPython && "Python".equalsIgnoreCase(githubLanguage)) return "PYTHON";
            if (isCpp && ("C++".equalsIgnoreCase(githubLanguage) || "C".equalsIgnoreCase(githubLanguage))) return "CPLUSPLUS";
        }
        
        // Secondary tie-breaker: File counts
        if (isFlutter || isSpring || isReact || isAngular || isVue || isNode || isDjango || isPython || isCpp) {
            long maxCount = Math.max(Math.max(javaCount, jsCount + tsCount), Math.max(pythonCount, cppCount));
            
            // If Flutter is an option, Dart files would dominate. But since we didn't count dart natively, 
            // we give it priority if pubspec is found and maxCount is low, or if the user requests it.
            if (isFlutter && (maxCount < 10 || "Dart".equalsIgnoreCase(githubLanguage))) return "FLUTTER";
            
            if (maxCount == javaCount && isSpring) return "SPRING_BOOT";
            if (maxCount == (jsCount + tsCount)) {
                if (isReact) return "REACT";
                if (isAngular) return "ANGULAR";
                if (isVue) return "VUE";
                if (isNode) return "NODE";
            }
            if (maxCount == pythonCount) {
                if (isDjango) return "DJANGO";
                if (isPython) return "PYTHON";
            }
            if (maxCount == cppCount && isCpp) return "CPLUSPLUS";
            
            // Fallbacks if counts match exactly or are 0
            if (isFlutter) return "FLUTTER";
            if (isSpring) return "SPRING_BOOT";
            if (isReact) return "REACT";
            if (isNode) return "NODE";
            if (isDjango) return "DJANGO";
            if (isPython) return "PYTHON";
        }

        // Language defaults if no framework markers
        if ("Java".equalsIgnoreCase(githubLanguage)) return "JAVA";
        if ("Python".equalsIgnoreCase(githubLanguage)) return "PYTHON";
        if ("JavaScript".equalsIgnoreCase(githubLanguage) || "TypeScript".equalsIgnoreCase(githubLanguage)) return "JAVASCRIPT";
        if ("C++".equalsIgnoreCase(githubLanguage) || "C".equalsIgnoreCase(githubLanguage)) return "CPLUSPLUS";

        // HTML/CSS / Portfolios / Docs
        if (markdownCount >= (totalFiles * 0.5) && totalFiles > 3) {
            return "DOCUMENTATION_REPOSITORY";
        }

        if (totalFiles <= 3 && javaCount == 0 && pythonCount == 0 && jsCount == 0 && tsCount == 0 && cppCount == 0) {
            if (markdownCount > 0) {
                return "README_ONLY";
            }
        }

        if ((htmlCount > 0 || cssCount > 0) && javaCount == 0 && pythonCount == 0 && jsCount == 0 && cppCount == 0) {
            if (totalFiles < 20) return "PORTFOLIO";
            return "HTML_CSS";
        }

        return "UNKNOWN";
    }

    private long countSuffix(List<String> files, String suffix) {
        return files.stream().filter(f -> f.toLowerCase().endsWith(suffix)).count();
    }

    private boolean hasFile(List<String> files, String exactName) {
        return files.stream().anyMatch(f -> f.equalsIgnoreCase(exactName) || f.toLowerCase().endsWith("/" + exactName.toLowerCase()));
    }
    
    private boolean hasMatch(List<String> files, String regex) {
        return files.stream().anyMatch(f -> f.toLowerCase().matches(regex));
    }
}
