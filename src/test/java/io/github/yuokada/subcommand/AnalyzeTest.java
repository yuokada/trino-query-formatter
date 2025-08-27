package io.github.yuokada.subcommand;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AnalyzeTest {

  private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
  private final PrintStream originalOut = System.out;

  @BeforeEach
  public void setUpStreams() {
    System.setOut(new PrintStream(outContent));
  }

  @AfterEach
  public void restoreStreams() {
    System.setOut(originalOut);
  }

  @Test
  void testCallWithFile(@TempDir Path tempDir) throws IOException {
    Path sqlFile = tempDir.resolve("test.sql");
    String sql = "SELECT * FROM catalog1.schema.tbl1; SELECT * FROM catalog2.schema.tbl2;";
    Files.writeString(sqlFile, sql);

    Analyze analyze = new Analyze();
    try {
      java.lang.reflect.Field field = analyze.getClass().getDeclaredField("sqlFile");
      field.setAccessible(true);
      field.set(analyze, sqlFile.toString());
    } catch (ReflectiveOperationException e) {
      throw new IOException(e);
    }
    analyze.call();

    String expectedOutput = """
        =========================
        Catalogs: [catalog1]
        =========================
        Catalogs: [catalog2]
        """.trim();
    assertEquals(expectedOutput, outContent.toString().trim());
  }

  @Test
  void testCallWithFile_NoCatalogs(@TempDir Path tempDir) throws IOException {
    Path sqlFile = tempDir.resolve("test_no_catalogs.sql");
    String sql = "SHOW CATALOGS;";
    Files.writeString(sqlFile, sql);

    Analyze analyze = new Analyze();
    try {
      java.lang.reflect.Field field = analyze.getClass().getDeclaredField("sqlFile");
      field.setAccessible(true);
      field.set(analyze, sqlFile.toString());
    } catch (ReflectiveOperationException e) {
      throw new IOException(e);
    }
    analyze.call();

    String expectedOutput = """
        =========================
        No catalogs found.
        """.trim();
    assertEquals(expectedOutput, outContent.toString().trim());
  }
}
