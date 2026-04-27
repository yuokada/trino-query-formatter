package io.github.yuokada.subcommand;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.yuokada.EntryCommand;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JsonRpcTest {

    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private final java.io.InputStream originalIn = System.in;

    @BeforeEach
    void setUpStreams() {
        System.setOut(new PrintStream(outContent));
    }

    @AfterEach
    void restoreStreams() {
        System.setOut(originalOut);
        System.setIn(originalIn);
    }

    @Test
    void jsonRpc_formatAndAnalyze() {
        String input = """
            {"jsonrpc":"2.0","id":1,"method":"format","params":{"sql":"select * from foo;"}}
            {"jsonrpc":"2.0","id":2,"method":"analyze","params":{"sql":"SELECT * FROM catalog1.s.t;"}}
            """;
        System.setIn(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));

        int exit = EntryCommand.newCommandLine().execute("json-rpc");
        String out = outContent.toString(StandardCharsets.UTF_8);

        assertEquals(0, exit);
        assertTrue(out.contains("\"id\":1"), "Response for id=1 should exist");
        assertTrue(out.contains("\"sql\":\"SELECT *"), "Formatted SQL should be returned");
        assertTrue(out.contains("\"id\":2"), "Response for id=2 should exist");
        assertTrue(out.contains("\"analysis\""), "Analyze result should be returned");
    }
}
