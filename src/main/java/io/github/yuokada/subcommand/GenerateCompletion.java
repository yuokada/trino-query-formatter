package io.github.yuokada.subcommand;

import io.github.yuokada.EntryCommand;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import picocli.AutoComplete;
import picocli.CommandLine;
import picocli.CommandLine.Model.ArgSpec;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.Spec;

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
        CommandLine root = EntryCommand.newCommandLine();
        CommandSpec rootSpec = root.getCommandSpec();
        String rootName = rootSpec.name();
        StringBuilder script = new StringBuilder();

        script.append("complete -c ").append(rootName).append(" -f\n");
        appendFishCommand(script, rootSpec, rootName, null);

        spec.commandLine().getOut().print(script);
        spec.commandLine().getOut().flush();
        return 0;
    }

    private void appendFishCommand(
        StringBuilder script, CommandSpec command, String rootName, String path) {
        String condition = path == null
            ? "__fish_use_subcommand"
            : "__fish_seen_subcommand_from " + path;
        appendFishOptions(script, command.options(), rootName, condition);
        for (Map.Entry<String, CommandLine> entry : directSubcommands(command).entrySet()) {
            String name = entry.getKey();
            CommandSpec subcommand = entry.getValue().getCommandSpec();
            appendFishSubcommand(
                script, rootName, condition, name, firstDescriptionLine(subcommand));
            String nextPath = path == null ? name : path + " " + name;
            appendFishCommand(script, subcommand, rootName, nextPath);
        }
    }

    private static Map<String, CommandLine> directSubcommands(CommandSpec command) {
        Map<String, CommandLine> unique = new LinkedHashMap<>();
        for (Map.Entry<String, CommandLine> entry : command.subcommands().entrySet()) {
            CommandSpec sub = entry.getValue().getCommandSpec();
            unique.putIfAbsent(sub.name(), entry.getValue());
        }
        return unique;
    }

    private static void appendFishSubcommand(
        StringBuilder script, String rootName, String condition, String name, String description) {
        script.append("complete -c ").append(rootName)
            .append(" -n ").append(fishQuote(condition))
            .append(" -a ").append(fishQuote(name));
        if (!description.isBlank()) {
            script.append(" -d ").append(fishQuote(description));
        }
        script.append('\n');
    }

    private static void appendFishOptions(
        StringBuilder script, List<OptionSpec> options, String rootName, String condition) {
        for (OptionSpec option : options) {
            if (option.hidden()) {
                continue;
            }
            String longName = firstLongName(option);
            String shortName = firstShortName(option);
            if (longName == null && shortName == null) {
                continue;
            }

            script.append("complete -c ").append(rootName)
                .append(" -n ").append(fishQuote(condition));
            if (longName != null) {
                script.append(" -l ").append(longName.substring(2));
            }
            if (shortName != null) {
                script.append(" -s ").append(shortName.substring(1));
            }
            if (option.arity().max() > 0) {
                script.append(" -r");
            }
            List<String> candidates = optionCandidates(option);
            if (!candidates.isEmpty()) {
                script.append(" -a ").append(fishQuote(String.join(" ", candidates)));
            }
            String description = firstDescriptionLine(option);
            if (!description.isBlank()) {
                script.append(" -d ").append(fishQuote(description));
            }
            script.append('\n');
        }
    }

    private static String firstLongName(OptionSpec option) {
        for (String name : option.names()) {
            if (name.startsWith("--")) {
                return name;
            }
        }
        return null;
    }

    private static String firstShortName(OptionSpec option) {
        for (String name : option.names()) {
            if (name.startsWith("-") && !name.startsWith("--") && name.length() == 2) {
                return name;
            }
        }
        return null;
    }

    private static List<String> optionCandidates(OptionSpec option) {
        List<String> values = new ArrayList<>();
        Iterable<String> completionCandidates = option.completionCandidates();
        if (completionCandidates != null) {
            for (String candidate : completionCandidates) {
                if (candidate != null && !candidate.isBlank()) {
                    values.add(candidate.trim());
                }
            }
        }
        if (!values.isEmpty()) {
            return values;
        }
        Class<?> type = option.typeInfo().getType();
        if (type != null && type.isEnum()) {
            for (Object constant : type.getEnumConstants()) {
                values.add(constant.toString().toLowerCase(Locale.ROOT));
            }
        }
        return values;
    }

    private static String firstDescriptionLine(ArgSpec spec) {
        String[] descriptions = spec.description();
        if (descriptions == null || descriptions.length == 0) {
            return "";
        }
        return descriptions[0].trim();
    }

    private static String firstDescriptionLine(CommandSpec spec) {
        String[] descriptions = spec.usageMessage().description();
        if (descriptions == null || descriptions.length == 0) {
            return "";
        }
        return descriptions[0].trim();
    }

    private static String fishQuote(String value) {
        return "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }
}
