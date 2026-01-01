package io.github.yuokada.subcommand.output;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Emits lines either to stdout or to a file buffer when an output path is provided.
 */
public final class OutputEmitter {

  private final StringBuilder buffer;
  private final Path outPath;

  /**
   * Creates a new emitter.
   * @param outputPath The path to write to; when null or empty, writes to stdout.
   */
  public OutputEmitter(String outputPath) {
    if (outputPath == null || outputPath.isEmpty()) {
      this.outPath = null;
      this.buffer = null;
    } else {
      this.outPath = Path.of(outputPath);
      this.buffer = new StringBuilder();
    }
  }

  /**
   * Emits a single line.
   * @param line The line to emit.
   */
  public void emit(String line) {
    if (buffer == null) {
      System.out.println(line);
    } else {
      buffer.append(line).append(System.lineSeparator());
    }
  }

  /**
   * Flushes the internal buffer to the output file when in file mode.
   * @throws IOException when write fails.
   */
  public void close() throws IOException {
    if (buffer != null && outPath != null) {
      Files.writeString(outPath, buffer.toString());
    }
  }
}

