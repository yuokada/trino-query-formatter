package io.github.yuokada;

import io.github.yuokada.subcommand.Analyze;
import io.github.yuokada.subcommand.Format;
import io.github.yuokada.subcommand.GenerateCompletion;
import io.quarkus.picocli.runtime.annotations.TopCommand;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Properties;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.ParseResult;

@TopCommand
@CommandLine.Command(
    name = "trino-query-formatter",
    subcommands = {
        Analyze.class,
        Format.class,
        GenerateCompletion.class
    },
    mixinStandardHelpOptions = true,
    versionProvider = GitVersionProvider.class,
    exitCodeOnUsageHelp = ExitCode.OK,
    exitCodeOnVersionHelp = ExitCode.OK,
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
     * Explicit version flag so the command can customize verbose version output.
     */
    @CommandLine.Option(names = {"-V", "--version"}, versionHelp = true,
        description = "Print version information and exit.")
    private boolean versionRequested;

    /**
     * Main entry point for the command-line application.
     *
     * @param args Command-line arguments.
     * @throws IOException If an I/O error occurs.
     */
    public static void main(String[] args) throws IOException {
        int exitCode = newCommandLine().execute(args);
        System.exit(exitCode);
    }

    /**
     * Builds a configured command line with custom verbose version handling.
     *
     * @return configured command line
     */
    public static CommandLine newCommandLine() {
        CommandLine commandLine = new CommandLine(new EntryCommand());
        commandLine.setExecutionStrategy(EntryCommand::executeWithVersionHelp);
        return commandLine;
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

    private static int executeWithVersionHelp(ParseResult parseResult) {
        ParseResult root = parseResult;
        if (root.isVersionHelpRequested() && !root.hasSubcommand()) {
            EntryCommand command = (EntryCommand) root.commandSpec().userObject();
            PrintWriter out = root.commandSpec().commandLine().getOut();
            if (command.isVerbose()) {
                printVerboseVersion(out);
            } else {
                root.commandSpec().commandLine().printVersionHelp(out);
            }
            out.flush();
            return ExitCode.OK;
        }
        Integer helpResult = CommandLine.executeHelpRequest(parseResult);
        if (helpResult != null) {
            return ExitCode.OK;
        }
        return new CommandLine.RunLast().execute(parseResult);
    }

    private static void printVerboseVersion(PrintWriter out) {
        Properties gitProperties = VersionMetadata.gitProperties();
        out.println("trino-query-formatter " + VersionMetadata.applicationVersion());
        out.println("  Git commit  : "
            + gitProperties.getProperty("git.commit.id.abbrev", VersionMetadata.UNKNOWN));
        out.println("  Build date  : "
            + gitProperties.getProperty("git.build.time", VersionMetadata.UNKNOWN));
        out.println("  Java runtime: " + System.getProperty("java.version", VersionMetadata.UNKNOWN)
            + " (" + System.getProperty("java.vendor", VersionMetadata.UNKNOWN) + ")");
        out.println("  Quarkus     : "
            + VersionMetadata.dependencyVersion("io.quarkus", "quarkus-picocli"));
        out.println("  trino-parser: "
            + VersionMetadata.dependencyVersion("io.trino", "trino-parser"));
        out.println("  trino-cli   : "
            + VersionMetadata.dependencyVersion("io.trino", "trino-cli"));
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
