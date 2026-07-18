package com.omyadav.repodoctor.service;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class RepositoryFileFilterService {

    public List<String> filterUsefulFiles(
            Map<String, Object> repositoryTree) {

        Object treeObj = repositoryTree.get("tree");
        if (!(treeObj instanceof List<?>)) {
            return List.of();
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tree = (List<Map<String, Object>>) treeObj;

        return tree.stream()
                .filter(item -> "blob".equals(item.get("type")))
                .map(item -> item.get("path").toString())
                .filter(path -> !isIgnoredPath(path))
                .filter(this::isUsefulFile)
                .toList();
    }

    private boolean isIgnoredPath(String path) {

        String lowerPath = path.toLowerCase();

        return lowerPath.startsWith(".vs/")
                || lowerPath.startsWith(".vscode/")
                || lowerPath.contains("/.ipynb_checkpoints/")
                || lowerPath.startsWith(".ipynb_checkpoints/")
                || lowerPath.contains("/__pycache__/")
                || lowerPath.startsWith("__pycache__/")
                || lowerPath.endsWith(".pyc")
                || lowerPath.equals(".env")
                || lowerPath.equals(".ds_store")
                || lowerPath.endsWith("thumbs.db");
    }

    private boolean isUsefulFile(String path) {

        String lowerPath = path.toLowerCase();

        return lowerPath.endsWith(".java")
                || lowerPath.endsWith(".py")
                || lowerPath.endsWith(".js")
                || lowerPath.endsWith(".jsx")
                || lowerPath.endsWith(".ts")
                || lowerPath.endsWith(".tsx")
                || lowerPath.endsWith(".go")
                || lowerPath.endsWith(".rb")
                || lowerPath.endsWith(".rs")
                || lowerPath.endsWith(".kt")
                || lowerPath.endsWith(".kts")
                || lowerPath.endsWith(".php")
                || lowerPath.endsWith(".c")
                || lowerPath.endsWith(".cpp")
                || lowerPath.endsWith(".h")
                || lowerPath.endsWith(".hpp")
                || lowerPath.endsWith(".cs")
                || lowerPath.endsWith(".html")
                || lowerPath.endsWith(".css")
                || lowerPath.endsWith(".sql")
                || lowerPath.endsWith(".xml")
                || lowerPath.endsWith(".yml")
                || lowerPath.endsWith(".yaml")
                || lowerPath.endsWith(".properties")
                || lowerPath.endsWith(".json")
                || lowerPath.endsWith(".md")
                || lowerPath.endsWith(".ipynb")
                || lowerPath.endsWith(".sh")
                || lowerPath.endsWith("requirements.txt")
                || lowerPath.endsWith("pom.xml");
    }
}