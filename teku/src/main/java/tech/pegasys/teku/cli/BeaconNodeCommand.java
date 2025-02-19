/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.teku.cli;

import java.io.File;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionException;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.metrics.StandardMetricCategory;
import org.hyperledger.besu.plugin.services.metrics.MetricCategory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.ScopeType;
import picocli.CommandLine.Unmatched;
import tech.pegasys.teku.cli.converter.MetricCategoryConverter;
import tech.pegasys.teku.cli.converter.PicoCliVersionProvider;
import tech.pegasys.teku.cli.options.BeaconNodeDataOptions;
import tech.pegasys.teku.cli.options.BeaconRestApiOptions;
import tech.pegasys.teku.cli.options.Eth2NetworkOptions;
import tech.pegasys.teku.cli.options.ExecutionLayerOptions;
import tech.pegasys.teku.cli.options.InteropOptions;
import tech.pegasys.teku.cli.options.LoggingOptions;
import tech.pegasys.teku.cli.options.MetricsOptions;
import tech.pegasys.teku.cli.options.P2POptions;
import tech.pegasys.teku.cli.options.StoreOptions;
import tech.pegasys.teku.cli.options.ValidatorOptions;
import tech.pegasys.teku.cli.options.ValidatorRestApiOptions;
import tech.pegasys.teku.cli.options.WeakSubjectivityOptions;
import tech.pegasys.teku.cli.subcommand.GenesisCommand;
import tech.pegasys.teku.cli.subcommand.MigrateDatabaseCommand;
import tech.pegasys.teku.cli.subcommand.PeerCommand;
import tech.pegasys.teku.cli.subcommand.SlashingProtectionCommand;
import tech.pegasys.teku.cli.subcommand.TransitionCommand;
import tech.pegasys.teku.cli.subcommand.UnstableOptionsCommand;
import tech.pegasys.teku.cli.subcommand.ValidatorClientCommand;
import tech.pegasys.teku.cli.subcommand.VoluntaryExitCommand;
import tech.pegasys.teku.cli.subcommand.admin.AdminCommand;
import tech.pegasys.teku.cli.subcommand.admin.InternalToolsCommand;
import tech.pegasys.teku.cli.subcommand.debug.DebugToolsCommand;
import tech.pegasys.teku.cli.util.CascadingDefaultProvider;
import tech.pegasys.teku.cli.util.EnvironmentVariableDefaultProvider;
import tech.pegasys.teku.cli.util.YamlConfigFileDefaultProvider;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.infrastructure.exceptions.ExceptionUtil;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.logging.LoggingConfig;
import tech.pegasys.teku.infrastructure.logging.LoggingConfigurator;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.storage.server.DatabaseStorageException;

@SuppressWarnings("unused")
@Command(
    name = "teku",
    subcommands = {
      CommandLine.HelpCommand.class,
      AdminCommand.class,
      TransitionCommand.class,
      PeerCommand.class,
      GenesisCommand.class,
      SlashingProtectionCommand.class,
      MigrateDatabaseCommand.class,
      DebugToolsCommand.class,
      UnstableOptionsCommand.class,
      VoluntaryExitCommand.class,
      InternalToolsCommand.class,
    },
    showDefaultValues = true,
    abbreviateSynopsis = true,
    description = "Run the Teku beacon chain client and validator",
    mixinStandardHelpOptions = true,
    versionProvider = PicoCliVersionProvider.class,
    synopsisHeading = "%n",
    descriptionHeading = "%n@|bold Description:|@%n%n",
    optionListHeading = "%n@|bold Options:|@%n",
    footerHeading = "%n",
    footer = "Teku is licensed under the Apache License 2.0")
public class BeaconNodeCommand implements Callable<Integer> {

  public static final String LOG_FILE_PREFIX = "teku";

  public static final String CONFIG_FILE_OPTION_NAME = "--config-file";
  static final String TEKU_CONFIG_FILE_ENV = "TEKU_CONFIG_FILE";
  private final PrintWriter outputWriter;
  private final PrintWriter errorWriter;
  private final Map<String, String> environment;
  private final StartAction startAction;
  private final LoggingConfigurator loggingConfigurator;
  private final MetricCategoryConverter metricCategoryConverter = new MetricCategoryConverter();

  // allows two pass approach to obtain optional config file
  private static class ConfigFileCommand {

    @Option(
        names = {"-c", CONFIG_FILE_OPTION_NAME},
        arity = "1",
        // Available to all subcommands
        scope = ScopeType.INHERIT)
    File configFile;

    @SuppressWarnings("UnunsedVariable")
    @Unmatched
    List<String> otherOptions;
  }

  @Option(
      names = {"-c", CONFIG_FILE_OPTION_NAME},
      paramLabel = "<FILENAME>",
      description = "Path/filename of the yaml config file (default: none)",
      arity = "1",
      // Available to all subcommands
      scope = ScopeType.INHERIT)
  private File configFile;

  @CommandLine.ArgGroup(validate = false, heading = "Network%n")
  private Eth2NetworkOptions eth2NetworkOptions = new Eth2NetworkOptions();

  @CommandLine.ArgGroup(validate = false, heading = "P2P%n")
  private P2POptions p2POptions = new P2POptions();

  @CommandLine.ArgGroup(validate = false, heading = "Validator%n")
  private ValidatorOptions validatorOptions = new ValidatorOptions();

  @CommandLine.ArgGroup(validate = false, heading = "Execution Layer%n")
  private ExecutionLayerOptions executionLayerOptions = new ExecutionLayerOptions();

  @CommandLine.ArgGroup(validate = false, heading = "Data Storage%n")
  private BeaconNodeDataOptions beaconNodeDataOptions = new BeaconNodeDataOptions();

  @CommandLine.ArgGroup(validate = false, heading = "Beacon REST API%n")
  private BeaconRestApiOptions beaconRestApiOptions = new BeaconRestApiOptions();

  @CommandLine.ArgGroup(validate = false, heading = "Validator REST API%n")
  private ValidatorRestApiOptions validatorRestApiOptions = new ValidatorRestApiOptions();

  @CommandLine.ArgGroup(validate = false, heading = "Weak Subjectivity%n")
  private WeakSubjectivityOptions weakSubjectivityOptions = new WeakSubjectivityOptions();

  @Mixin(name = "Interop")
  private InteropOptions interopOptions = new InteropOptions();

  @Mixin(name = "Store")
  private final StoreOptions storeOptions = new StoreOptions();

  @CommandLine.ArgGroup(validate = false, heading = "Logging%n")
  private final LoggingOptions loggingOptions = new LoggingOptions();

  @CommandLine.ArgGroup(validate = false, heading = "Metrics%n")
  private MetricsOptions metricsOptions = new MetricsOptions();

  @CommandLine.Spec private CommandLine.Model.CommandSpec spec;

  private final ValidatorClientCommand validatorClientSubcommand;

  public BeaconNodeCommand(
      final PrintWriter outputWriter,
      final PrintWriter errorWriter,
      final Map<String, String> environment,
      final StartAction startAction,
      final LoggingConfigurator loggingConfigurator) {
    this.outputWriter = outputWriter;
    this.errorWriter = errorWriter;
    this.environment = environment;
    this.startAction = startAction;
    this.loggingConfigurator = loggingConfigurator;

    metricCategoryConverter.addCategories(TekuMetricCategory.class);
    metricCategoryConverter.addCategories(StandardMetricCategory.class);
    this.validatorClientSubcommand = new ValidatorClientCommand(loggingOptions);
  }

  private CommandLine configureCommandLine(final CommandLine commandLine) {
    return commandLine
        .registerConverter(MetricCategory.class, metricCategoryConverter)
        .registerConverter(UInt64.class, UInt64::valueOf)
        .setUnmatchedOptionsAllowedAsOptionParameters(false);
  }

  private CommandLine getConfigFileCommandLine(final ConfigFileCommand configFileCommand) {
    return configureCommandLine(new CommandLine(configFileCommand));
  }

  private CommandLine getCommandLine() {
    return configureCommandLine(new CommandLine(this)).addSubcommand(validatorClientSubcommand);
  }

  public int parse(final String[] args) {
    // first pass to obtain config file if specified and print usage/version help
    final ConfigFileCommand configFileCommand = new ConfigFileCommand();
    final CommandLine configFileCommandLine = getConfigFileCommandLine(configFileCommand);
    configFileCommandLine.parseArgs(args);
    if (configFileCommandLine.isUsageHelpRequested()) {
      return executeCommandUsageHelp();
    } else if (configFileCommandLine.isVersionHelpRequested()) {
      return executeCommandVersion();
    }

    final Optional<File> configFile = getConfigFileFromCliOrEnv(configFileCommand);

    // final pass
    final CommandLine commandLine = getCommandLine();
    commandLine.setCaseInsensitiveEnumValuesAllowed(true);
    commandLine.setOut(outputWriter);
    commandLine.setErr(errorWriter);
    commandLine.setParameterExceptionHandler(this::handleParseException);
    commandLine.setDefaultValueProvider(defaultValueProvider(commandLine, configFile));

    return commandLine.execute(args);
  }

  private Optional<File> getConfigFileFromCliOrEnv(final ConfigFileCommand configFileCommand) {
    return Optional.ofNullable(configFileCommand.configFile)
        .or(() -> Optional.ofNullable(environment.get(TEKU_CONFIG_FILE_ENV)).map(File::new));
  }

  private int executeCommandVersion() {
    final CommandLine baseCommandLine = getCommandLine();
    baseCommandLine.printVersionHelp(outputWriter);
    return baseCommandLine.getCommandSpec().exitCodeOnVersionHelp();
  }

  private int executeCommandUsageHelp() {
    final CommandLine baseCommandLine = getCommandLine();
    baseCommandLine.usage(outputWriter);
    return baseCommandLine.getCommandSpec().exitCodeOnUsageHelp();
  }

  private CommandLine.IDefaultValueProvider defaultValueProvider(
      final CommandLine commandLine, final Optional<File> configFile) {
    if (configFile.isEmpty()) {
      return new EnvironmentVariableDefaultProvider(environment);
    }

    return new CascadingDefaultProvider(
        new EnvironmentVariableDefaultProvider(environment),
        new YamlConfigFileDefaultProvider(commandLine, configFile.get()));
  }

  private int handleParseException(final CommandLine.ParameterException ex, final String[] args) {
    errorWriter.println(ex.getMessage());

    CommandLine.UnmatchedArgumentException.printSuggestions(ex, errorWriter);
    printUsage(errorWriter);

    return ex.getCommandLine().getCommandSpec().exitCodeOnInvalidInput();
  }

  private void printUsage(PrintWriter outputWriter) {
    outputWriter.println();
    outputWriter.println("To display full help:");
    outputWriter.println("teku [COMMAND] --help");
  }

  public boolean isOptionSpecified(String optionLongName) {
    var parseResult = spec.commandLine().getParseResult();
    var option = spec.findOption(optionLongName);
    return option != null && parseResult.hasMatchedOption(option);
  }

  @Override
  public Integer call() {
    try {
      startLogging();
      final TekuConfiguration tekuConfig = tekuConfiguration();
      startAction.start(tekuConfig, false);
      return 0;
    } catch (InvalidConfigurationException | DatabaseStorageException ex) {
      reportUserError(ex);
    } catch (CompletionException e) {
      ExceptionUtil.<Throwable>getCause(e, InvalidConfigurationException.class)
          .or(() -> ExceptionUtil.getCause(e, DatabaseStorageException.class))
          .ifPresentOrElse(this::reportUserError, () -> reportUnexpectedError(e));
    } catch (Throwable t) {
      reportUnexpectedError(t);
    }
    return 1;
  }

  public void reportUnexpectedError(final Throwable t) {
    getLogger().fatal("Teku failed to start", t);
    errorWriter.println("Teku failed to start: " + t.getMessage());
    printUsage(errorWriter);
  }

  public void reportUserError(final Throwable ex) {
    getLogger().fatal(ex.getMessage(), ex);
    errorWriter.println(ex.getMessage());
    printUsage(errorWriter);
  }

  /**
   * Not using a static field for this log instance because some code in this class executes prior
   * to the logging configuration being applied so it's not always safe to use the logger.
   *
   * <p>Where this is used we also ensure the messages are printed to the error writer so they will
   * be printed even if logging is not yet configured.
   *
   * @return the logger for this class
   */
  private Logger getLogger() {
    return LogManager.getLogger();
  }

  private void startLogging() {
    LoggingConfig loggingConfig =
        buildLoggingConfig(beaconNodeDataOptions.getDataPath(), LOG_FILE_PREFIX);
    loggingConfigurator.startLogging(loggingConfig);
    // jupnp logs a lot of context to level WARN, and it is quite verbose.
    LoggingConfigurator.setAllLevelsSilently("org.jupnp", Level.ERROR);
  }

  public LoggingConfig buildLoggingConfig(
      final String logDirectoryPath, final String logFilePrefix) {
    return loggingOptions.applyLoggingConfiguration(logDirectoryPath, logFilePrefix);
  }

  public StartAction getStartAction() {
    return startAction;
  }

  protected TekuConfiguration tekuConfiguration() {
    try {
      TekuConfiguration.Builder builder = TekuConfiguration.builder();
      // Eth2NetworkOptions configures network defaults across builders, so configure this first
      eth2NetworkOptions.configure(builder);
      executionLayerOptions.configure(builder);
      weakSubjectivityOptions.configure(builder);
      validatorOptions.configure(builder);
      p2POptions.configure(builder);
      beaconRestApiOptions.configure(builder);
      validatorRestApiOptions.configure(builder);
      loggingOptions.configureWireLogs(builder);
      interopOptions.configure(builder);
      beaconNodeDataOptions.configure(builder);
      metricsOptions.configure(builder);
      storeOptions.configure(builder);

      return builder.build();
    } catch (IllegalArgumentException | NullPointerException e) {
      throw new InvalidConfigurationException(e);
    }
  }

  public LoggingConfigurator getLoggingConfigurator() {
    return loggingConfigurator;
  }

  @FunctionalInterface
  public interface StartAction {
    void start(TekuConfiguration config, boolean validatorClient);
  }
}
