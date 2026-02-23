package io.github.yuokada;

import io.github.yuokada.subcommand.Analyze;
import io.github.yuokada.subcommand.Format;
import io.quarkus.picocli.runtime.annotations.TopCommand;
import java.io.IOException;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.ExitCode;

@TopCommand
@CommandLine.Command(
    name = "trino-query-formatter",
    subcommands = {
        Analyze.class,
        Format.class
    },
    mixinStandardHelpOptions = true,
    versionProvider = GitVersionProvider.class,
    description = "Tool to format SQL queries for Trino.")
public class EntryCommand implements Callable<Integer> {

    /**
     * Enable verbose output (print file names, statement counts, etc. to stderr).
     */
    @CommandLine.Option(names = {"-v", "--verbose"}, description = "Enable verbose output.")
    private boolean verbose;

    /**
     * Suppress non-error output (e.g. 'Would reformat' messages in --check mode).
     */
    @CommandLine.Option(names = {"--quiet"}, description = "Suppress non-error output.")
    private boolean quiet;

    /**
     * Main entry point for the command-line application.
     *
     * @param args Command-line arguments.
     * @throws IOException If an I/O error occurs.
     */
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

    /**
     * Returns {@code true} if verbose output is enabled.
     *
     * @return true when the {@code -v}/{@code --verbose} flag was supplied
     */
    public boolean isVerbose() {
        return this.verbose;
    }

    /**
     * Returns {@code true} if quiet mode is enabled.
     *
     * @return true when the {@code --quiet} flag was supplied
     */
    public boolean isQuiet() {
        return this.quiet;
    }
}
