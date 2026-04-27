package io.github.yuokada.subcommand;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.yuokada.EntryCommand;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class GenerateCompletionTest {

    @Test
    void fishCompletion_containsCurrentCommandOptions() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CommandLine commandLine = EntryCommand.newCommandLine();
        commandLine.setOut(new PrintWriter(
            new OutputStreamWriter(out, StandardCharsets.UTF_8), true));

        int exit = commandLine.execute("generate-completion", "--shell", "fish");
        String script = out.toString(StandardCharsets.UTF_8);

        assertEquals(0, exit);
        assertTrue(script.contains("complete -c trino-query-formatter -f"));
        assertTrue(script.contains(
            "complete -c trino-query-formatter -n '__fish_seen_subcommand_from format'"
                + " -l dir -r"));
        assertTrue(script.contains(
            "complete -c trino-query-formatter -n '__fish_seen_subcommand_from format'"
                + " -l exclude -r"));
        assertTrue(script.contains(
            "complete -c trino-query-formatter -n '__fish_seen_subcommand_from analyze'"
                + " -l explain-timeout -r"));
    }
}
