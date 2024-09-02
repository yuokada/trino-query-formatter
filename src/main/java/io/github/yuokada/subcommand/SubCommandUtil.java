package io.github.yuokada.subcommand;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public interface SubCommandUtil {

  default String readFromFile(String sqlFile) throws IOException {
    return String.join("\n", Files
        .readAllLines(Path.of(sqlFile), StandardCharsets.UTF_8));
  }
}
