package io.github.yuokada.subcommand.output;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Emits lines either to stdout or to a file buffer when an output path is provided.
 */
public final class OutputEmitter implements AutoCloseable {

    private final Path outPath;
    private BufferedWriter writer; // non-null when writing to file incrementally
    private boolean closed = false;

    /**
     * Creates a new emitter.
     *
     * @param outputPath The path to write to; when null or empty, writes to stdout.
     */
    public OutputEmitter(String outputPath) {
        if (outputPath == null || outputPath.isEmpty()) {
            this.outPath = null;
        } else {
            this.outPath = Path.of(outputPath);
            try {
                Path parent = this.outPath.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                this.writer = Files.newBufferedWriter(this.outPath, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Emits a single line.
     *
     * @param line The line to emit.
     * @throws IllegalStateException if called after close().
     */
    public void emit(String line) {
        if (closed && outPath != null) {
            throw new IllegalStateException("Cannot emit after OutputEmitter has been closed");
        }
        if (writer != null) {
            try {
                writer.write(line);
                writer.newLine();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            System.out.println(line);
        }
    }

    /**
     * Flushes the internal buffer to the output file when in file mode.
     *
     * @throws IOException when write fails.
     */
    public void close() throws IOException {
        if (writer != null) {
            writer.flush();
            writer.close();
            writer = null;
        }
        closed = true;
    }
}
