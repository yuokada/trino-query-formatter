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

class FormatTest {

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
    String sql = "select * from foo;select * from bar;";
    Files.writeString(sqlFile, sql);

    Format format = new Format();
    try {
      java.lang.reflect.Field field = format.getClass().getDeclaredField("sqlFile");
      field.setAccessible(true);
      field.set(format, sqlFile.toString());
    } catch (ReflectiveOperationException e) {
      throw new IOException(e);
    }
    format.call();

    String expectedOutput = """
        SELECT *
        FROM
          foo
        ;
        SELECT *
        FROM
          bar
        ;""".trim();
    assertEquals(expectedOutput, outContent.toString().trim());
  }
}
