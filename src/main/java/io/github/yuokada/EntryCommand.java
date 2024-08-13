package io.github.yuokada;

import io.github.yuokada.subcommand.Analyze;
import io.github.yuokada.subcommand.Format;
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
        Format.class
    },
    mixinStandardHelpOptions = true,
    version = "0.1",
    description = "Tool to format SQL queries for Trino.")
public class EntryCommand implements Callable<Integer> {

  private static final Logger logger = LoggerFactory.getLogger(EntryCommand.class);

  @Option(
      names = {"-V", "--version"},
      versionHelp = true,
      description = "print version information and exit")
  boolean versionRequested;

  @Option(
      names = {"--help", "-h"},
      usageHelp = true)
  boolean help;

  public static void main(String[] args) throws IOException {
    int exitCode = new CommandLine(new EntryCommand()).execute(args);
    System.exit(exitCode);
  }

  @Override
  public Integer call() throws Exception {
    CommandLine.usage(this, System.out);
    // Quarkus.waitForExit();
    return ExitCode.OK;
  }
}
