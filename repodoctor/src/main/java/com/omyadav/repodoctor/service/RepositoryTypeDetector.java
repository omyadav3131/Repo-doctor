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
        if (hasPomXml || hasBuildGradle) {
            if (javaCount > 0) return "SPRING_BOOT";
            return "JAVA";
        }

        if (hasPackageJson) {
            if (hasReactMarkers) return "REACT";
            if (hasAngularMarkers) return "ANGULAR";
            if (hasVueMarkers) return "VUE";
            return "NODE";
        }

        if (hasRequirementsTxt || hasManagePy) {
            if (hasManagePy) return "DJANGO";
            return "PYTHON";
        }

        if (hasFlutterMarkers) {
            return "FLUTTER";
        }
        
        long cppCount = countSuffix(allFiles, ".cpp") + countSuffix(allFiles, ".c") + countSuffix(allFiles, ".h") + countSuffix(allFiles, ".hpp");
        if (cppCount > 0 || hasFile(allFiles, "CMakeLists.txt")) {
            return "CPLUSPLUS";
        }

        // Language defaults
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
}
