package com.omyadav.repodoctor.service;

import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Set;

@Service
public class ExcludedPathService {

    private static final Set<String> EXCLUDED_DIRECTORIES = Set.of(
            "node_modules", "vendor", "dist", "build", "target", "out", "bin",
            "coverage", ".git", ".next", ".nuxt", "venv", ".venv", "__pycache__",
            ".gradle", ".idea", ".vs", ".vscode", ".ipynb_checkpoints"
    );

    /**
     * Determines whether a given path is an excluded directory or within one.
     * It checks each segment of the path exactly, avoiding substring false positives.
     *
     * @param path the file or directory path
     * @return true if the path contains an excluded directory segment
     */
    public boolean isVendorOrBuildPath(String path) {
        return getMatchedVendorOrBuildPath(path) != null;
    }

    public String getMatchedVendorOrBuildPath(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }

        String lowerPath = path.toLowerCase(Locale.ROOT);
        String[] segments = lowerPath.split("/");

        for (String segment : segments) {
            if (EXCLUDED_DIRECTORIES.contains(segment)) {
                return segment;
            }
        }
        
        return null;
    }
}
