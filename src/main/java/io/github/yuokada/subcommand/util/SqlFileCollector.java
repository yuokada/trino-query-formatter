package io.github.yuokada.subcommand.util;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Utility for collecting SQL files from a directory tree.
 */
public final class SqlFileCollector {

    private SqlFileCollector() {
    }

    /**
     * Recursively collects {@code *.sql} files under a directory.
     *
     * @param dirPath  directory root
     * @param excludes glob patterns applied to paths relative to {@code dirPath}
     * @return sorted SQL file list
     * @throws IOException when traversal fails
     */
    public static List<Path> collect(String dirPath, List<String> excludes) throws IOException {
        Path baseDir = Path.of(dirPath);
        List<PathMatcher> excludeMatchers = buildMatchers(excludes);
        List<Path> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(baseDir)) {
            walk.filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)
                    .endsWith(".sql"))
                .filter(path -> !matchesAny(path, baseDir, excludeMatchers))
                .sorted()
                .forEach(files::add);
        }
        return files;
    }

    private static List<PathMatcher> buildMatchers(List<String> excludes) {
        List<PathMatcher> matchers = new ArrayList<>();
        if (excludes == null) {
            return matchers;
        }
        for (String exclude : excludes) {
            if (exclude != null && !exclude.isBlank()) {
                matchers.add(FileSystems.getDefault().getPathMatcher("glob:" + exclude));
            }
        }
        return matchers;
    }

    private static boolean matchesAny(Path path, Path baseDir, List<PathMatcher> matchers) {
        if (matchers.isEmpty()) {
            return false;
        }
        Path relative = baseDir.toAbsolutePath().normalize()
            .relativize(path.toAbsolutePath().normalize());
        for (PathMatcher matcher : matchers) {
            if (matcher.matches(relative)) {
                return true;
            }
        }
        return false;
    }
}
