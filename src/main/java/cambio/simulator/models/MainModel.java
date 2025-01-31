package cambio.simulator.models;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.TreeMap;

import cambio.simulator.export.ReportCollector;
import cambio.simulator.export.ReportWriter;
import cambio.simulator.misc.Util;
import cambio.simulator.parsing.GsonHelper;
import com.google.gson.Gson;
import desmoj.core.simulator.Experiment;
import desmoj.core.simulator.TimeInstant;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.io.FileUtils;

/**
 * Main class to start the experiment. This class will load the input file and create a model out of it.
 * doInitialSchedules Starts the inital event. initFields Gets called at the start of the experiment and loads all
 * relevant experiment resources.
 */
public class MainModel {

    /**
     * Main entry point of the program.
     *
     * @param args program options
     */
    public static void main(String[] args) {
        //Option parsing

        Options options = new Options();

        Option archModelOpt = new Option("a", "arch_model", true, "arch_model file path");
        archModelOpt.setRequired(true);
        options.addOption(archModelOpt);

        Option expModelOpt = new Option("e", "exp_model", true, "exp_model file path");
        expModelOpt.setRequired(false);
        options.addOption(expModelOpt);

        Option scenarioOpt = new Option("s", "scenario", true, "scenario file path");
        scenarioOpt.setRequired(false);
        options.addOption(scenarioOpt);

        Option progressbar = new Option("p", "progress_bar", false, "show progress bar during simulation");
        progressbar.setRequired(false);
        options.addOption(progressbar);

        Option debugOutput = new Option("d", "debug", false, "activate debugoutput");
        debugOutput.setRequired(false);
        options.addOption(debugOutput);


        CommandLineParser cmdParser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd;

        try {
            cmd = cmdParser.parse(options, args);
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            formatter.printHelp("Simulator", options);
            System.exit(1);
            return;
        }

        String archModel = cmd.getOptionValue(archModelOpt.getOpt(), null);
        String expModel = cmd.getOptionValue(expModelOpt.getOpt(), null);
        String scenarioLoc = cmd.getOptionValue(scenarioOpt.getOpt(), null);

        if (archModel == null) {
            System.out.println("No architecture was specified");
            System.exit(1);
            return;
        } else {
            File f = new File(archModel);
            if (!f.exists() || f.isDirectory()) {
                System.out.printf("Did not find architecture file at %s%n", f.getAbsolutePath());
                System.exit(1);
                return;
            }
        }


        File f;
        if (expModel == null) {
            System.out.println("No experiment was specified, checking for scenario description");
            if (scenarioLoc == null) {
                System.out.println("Scenario location was also not specified.");
                System.out.println("Exiting...");
                System.exit(1);
                return;
            } else {
                f = new File(scenarioLoc);
            }
        } else {
            f = new File(expModel);
        }
        if (!f.exists() || f.isDirectory()) {
            System.out.printf("Did not find file %s%n", f.getAbsolutePath());
            System.exit(1);
            return;
        }

        MiSimModel model = new MiSimModel(new File(archModel), new File(expModel));
        ExperimentMetaData metaData = model.getExperimentMetaData();

        if (cmd.hasOption(debugOutput.getOpt())) {
            model.debugOn();
        }

        Experiment exp = new Experiment(metaData.getExperimentName());
        model.connectToExperiment(exp);
        exp.setSeedGenerator(metaData.getSeed());
        exp.setShowProgressBarAutoclose(true);
        exp.setShowProgressBar(cmd.hasOption("p"));
        exp.stop(new TimeInstant(metaData.getDuration(), metaData.getTimeUnit()));
        exp.tracePeriod(new TimeInstant(0, metaData.getTimeUnit()),
            new TimeInstant(metaData.getDuration(), metaData.getTimeUnit()));
        exp.debugPeriod(new TimeInstant(0, metaData.getTimeUnit()),
            new TimeInstant(metaData.getDuration(), metaData.getTimeUnit()));
        if (cmd.hasOption(debugOutput.getOpt())) {
            exp.debugOn(new TimeInstant(0, metaData.getTimeUnit()));
        }


        final long experimentStartTime = System.nanoTime();

        //run experiment
        exp.start();

        final long experimentTime = System.nanoTime() - experimentStartTime;
        final long reportStartTime = System.nanoTime();

        //exp.report();
        exp.finish();

        //create report if wanted
        if (!metaData.getReportType().equals("none")) {
            generateReport(model);
        }

        final long reportTime = System.nanoTime() - reportStartTime;
        final long executionTime = reportTime + metaData.getDurationOfSetupMS() * 1000;

        System.out.println("\n*** Simulator ***");
        System.out.println("Simulation of Architecture: " + archModel);
        System.out.println("Executed Experiment:        " + metaData.getExperimentName());
        System.out.println("Setup took:                 " + Util.timeFormat(metaData.getDurationOfSetupMS() * 1000));
        System.out.println("Experiment took:            " + Util.timeFormat(experimentTime));
        System.out.println("Report took:                " + Util.timeFormat(reportTime));
        System.out.println("Execution took:             " + Util.timeFormat(executionTime));
    }

    private static void generateReport(MiSimModel model) {
        ExperimentMetaData metaData = model.getExperimentMetaData();
        Path reportLocation = Paths.get(".", "Report_" + metaData.getExperimentName());
        Gson gson = GsonHelper.getGson();
        try {
            FileUtils.deleteDirectory(reportLocation.toFile());
            reportLocation.toFile().mkdirs();
            String json = gson.toJson(metaData);
            Files.write(Paths.get(String.valueOf(reportLocation), "meta.json"), json.getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE);
            Files.copy(metaData.getArchFileLocation().toPath(), Paths.get(String.valueOf(reportLocation), "arch.json"));
            Files.copy(metaData.getExpFileLocation().toPath(), Paths.get(String.valueOf(reportLocation), "exp.json"));

            final PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher("glob:**/*.py");

            Files.walkFileTree(Paths.get("./Report"), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
                    if (pathMatcher.matches(path)) {
                        Files.copy(path, Paths.get(String.valueOf(reportLocation), path.toFile().getName()));
                    }
                    return FileVisitResult.CONTINUE;
                }
            });

            //export legacy graph
            //new ExportReport(model);
        } catch (IOException e) {
            e.printStackTrace();
        }

        HashMap<String, TreeMap<Double, Object>> data = ReportCollector.getInstance().collectData();
        TreeMap<String, TreeMap<Double, Object>> sortedData = new TreeMap<>(data);
        ReportWriter.writeReporterCollectorOutput(sortedData, reportLocation);

    }
}
