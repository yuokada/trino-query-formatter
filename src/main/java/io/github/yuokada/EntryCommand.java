package io.github.yuokada;

import io.github.yuokada.subcommand.Analyze;
import io.github.yuokada.subcommand.Format;
import io.github.yuokada.subcommand.Lexer;
import io.quarkus.picocli.runtime.annotations.TopCommand;
import java.io.IOException;
import java.util.concurrent.Callable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Option;

@TopCommand
@CommandLine.Command(
    name = "trino-query-formatter",
    subcommands = {
        Analyze.class,
        Lexer.class,
        Format.class
    },
    mixinStandardHelpOptions = true,
    version = "0.1",
    description = "Tool to format SQL queries for Trino.")
public class EntryCommand implements Callable<Integer> {

  private static final Logger logger = LoggerFactory.getLogger(EntryCommand.class);

      /**
     * The version of the command.
     */
    @CommandLine.Option(
            names = {"--version", "-V"},
            versionHelp = true,
            description = "print version information and exit")
    private boolean versionRequested;

    /**
     * Show help message.
     */
    @CommandLine.Option(
            names = {"--help", "-h"},
            usageHelp = true,
            description = "show this help message and exit")
    private boolean help;

  public static void main(String[] args) throws IOException {
    int exitCode = new CommandLine(new EntryCommand()).execute(args);
    System.exit(exitCode);
  }

      /**
     * The main entry point of the command.
     *
     * @return The exit code.
     * @throws Exception If an error occurs.
     */
    @Override
    public Integer call() throws Exception {
    CommandLine.usage(this, System.out);
    // Quarkus.waitForExit();
    return ExitCode.OK;
  }
}
