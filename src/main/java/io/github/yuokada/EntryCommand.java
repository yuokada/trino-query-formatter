package io.github.yuokada;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "greeting", mixinStandardHelpOptions = true)
public class EntryCommand implements Runnable {

  private static final Logger logger = LoggerFactory.getLogger(EntryCommand.class);

  @Option(names = {"-V", "--version"},
      versionHelp = true,
      description = "print version information and exit")
  boolean versionRequested;

  @Option(names = {"--help", "-h"}, usageHelp = true)
  boolean help;

  @Parameters(
      paramLabel = "<name>",
      defaultValue = "picocli",
      description = "Your name.")
  String name;

  @Override
  public void run() {
    System.out.printf("Hello %s, go go commando!\n", name);
  }

}
