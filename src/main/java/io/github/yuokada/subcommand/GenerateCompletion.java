package io.github.yuokada.subcommand;

import io.github.yuokada.EntryCommand;
import java.util.Locale;
import java.util.concurrent.Callable;
import picocli.AutoComplete;
import picocli.CommandLine;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

/**
 * Generates shell completion scripts for the CLI.
 */
@CommandLine.Command(
    name = "generate-completion",
    description = "Generate shell completion scripts.")
public class GenerateCompletion implements Callable<Integer> {

    /**
     * Shell type to generate.
     */
    @CommandLine.Option(names = {"--shell"}, defaultValue = "bash",
        description = "Shell type: bash, zsh, or fish.")
    private String shell;

    /**
     * Command specification for accessing the root command.
     */
    @Spec
    private CommandSpec spec;

    @Override
    public Integer call() {
        String shellName = this.shell.toLowerCase(Locale.ROOT);
        return switch (shellName) {
            case "bash", "zsh" -> printBashCompatibleScript();
            case "fish" -> printFishScript();
            default -> throw new CommandLine.ParameterException(spec.commandLine(),
                "Unsupported shell: " + this.shell + " (expected: bash, zsh, fish)");
        };
    }

    private int printBashCompatibleScript() {
        CommandLine root = EntryCommand.newCommandLine();
        String script = AutoComplete.bash(spec.root().name(), root);
        spec.commandLine().getOut().print(script);
        spec.commandLine().getOut().print('\n');
        spec.commandLine().getOut().flush();
        return 0;
    }

    private int printFishScript() {
        String rootName = spec.root().name();
        String script = """
            complete -c %s -f
            complete -c %s -n '__fish_use_subcommand' -a format -d 'Format SQL query'
            complete -c %s -n '__fish_use_subcommand' -a analyze -d 'Analyze SQL query'
            complete -c %s -n '__fish_use_subcommand' -a generate-completion -d 'Generate shell completion scripts'
            complete -c %s -n '__fish_seen_subcommand_from format' -l output -s o -r -d 'Write output to this file instead of stdout'
            complete -c %s -n '__fish_seen_subcommand_from format' -l check -d 'Check if input is already formatted'
            complete -c %s -n '__fish_seen_subcommand_from format' -l diff -d 'Show unified diff of formatting changes'
            complete -c %s -n '__fish_seen_subcommand_from format' -l keyword-case -r -a 'upper lower keep' -d 'SQL keyword case'
            complete -c %s -n '__fish_seen_subcommand_from format' -l indent-size -r -d 'Spaces per indentation level'
            complete -c %s -n '__fish_seen_subcommand_from format' -l max-line-length -r -d 'Warn when formatted lines exceed this length'
            complete -c %s -n '__fish_seen_subcommand_from analyze' -l format -r -a 'text json' -d 'Output format'
            complete -c %s -n '__fish_seen_subcommand_from analyze' -l details -r -a 'basic full' -d 'Detail level'
            complete -c %s -n '__fish_seen_subcommand_from analyze' -l output -r -d 'Write output to the specified file path'
            complete -c %s -n '__fish_seen_subcommand_from analyze' -l show-ast -d 'Show AST for each statement'
            complete -c %s -n '__fish_seen_subcommand_from analyze' -l ast-view -r -a 'tree outline raw' -d 'AST display mode'
            complete -c %s -n '__fish_seen_subcommand_from analyze' -l ast-depth -r -d 'Maximum AST depth to display'
            complete -c %s -n '__fish_seen_subcommand_from analyze' -l ast-limit -r -d 'Maximum characters for embedded AST in JSON output'
            complete -c %s -n '__fish_seen_subcommand_from analyze' -l catalog -r -d 'Default catalog'
            complete -c %s -n '__fish_seen_subcommand_from analyze' -l schema -r -d 'Default schema'
            complete -c %s -n '__fish_seen_subcommand_from analyze' -l validate-functions -d 'Flag unknown functions as W002'
            complete -c %s -n '__fish_seen_subcommand_from analyze' -l known-functions -r -d 'Extra known function names'
            complete -c %s -n '__fish_seen_subcommand_from analyze' -l udf-catalog -r -d 'YAML file with UDF definitions'
            complete -c %s -n '__fish_seen_subcommand_from analyze' -l server -r -d 'Trino server for remote validation'
            complete -c %s -n '__fish_seen_subcommand_from analyze' -l server-user -r -d 'User name for the Trino session'
            complete -c %s -n '__fish_seen_subcommand_from analyze' -l server-password -r -d 'Password for Basic auth'
            complete -c %s -n '__fish_seen_subcommand_from analyze' -l server-access-token -r -d 'Bearer token for OAuth2 or JWT'
            complete -c %s -n '__fish_seen_subcommand_from analyze' -l server-ssl -d 'Enable TLS for the Trino connection'
            complete -c %s -n '__fish_seen_subcommand_from analyze' -l server-ssl-trust-all -d 'Disable TLS certificate verification'
            complete -c %s -n '__fish_seen_subcommand_from analyze' -l explain-timeout -r -d 'Timeout in seconds for remote validation'
            complete -c %s -n '__fish_seen_subcommand_from generate-completion' -l shell -r -a 'bash zsh fish' -d 'Shell type'
            """.formatted(
            rootName, rootName, rootName, rootName, rootName, rootName, rootName, rootName,
            rootName, rootName, rootName, rootName, rootName, rootName, rootName, rootName,
            rootName, rootName, rootName, rootName, rootName, rootName, rootName, rootName,
            rootName, rootName, rootName, rootName, rootName, rootName, rootName, rootName);
        spec.commandLine().getOut().print(script);
        spec.commandLine().getOut().flush();
        return 0;
    }
}
