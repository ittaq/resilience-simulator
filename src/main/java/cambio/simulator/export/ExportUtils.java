package cambio.simulator.export;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import cambio.simulator.cli.CLI;
import cambio.simulator.misc.JarUtil;
import cambio.simulator.models.ExperimentMetaData;
import cambio.simulator.models.MiSimModel;
import cambio.simulator.parsing.GsonHelper;
import com.google.gson.Gson;

/**
 * Utility functions for creating the experiment report.
 *
 * @author Lion Wagner
 */
public final class ExportUtils {


    public static Path prepareReportFolder(MiSimModel model) {
        return prepareReportFolder(model.getExperimentMetaData());
    }

    public static Path prepareReportFolder(ExperimentMetaData metaData) {
        Path reportLocationBaseDirectory;
        if (CLI.reportLocation.getValue() != null) {
            reportLocationBaseDirectory = Paths.get(CLI.reportLocation.getValue());
        } else {
            reportLocationBaseDirectory = metaData.getReportBaseFolder();
        }

        DateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH-mm-ssZ");
        String dateString = format.format(new Date());
        Path reportLocation = Paths.get(reportLocationBaseDirectory.toString(),
            metaData.getExperimentName() + "_" + dateString);

        Gson gson = GsonHelper.getGsonBuilder().serializeNulls().create();

        try {
            Files.createDirectory(reportLocation);
            //copy metadata, architecture and experiment
            String json = gson.toJson(metaData);

            Files.write(Paths.get(reportLocation.toString(), "meta.json"),
                json.getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE);
            Files.copy(metaData.getArchitectureDescriptionLocation().toPath(),
                Paths.get(reportLocation.toString(), "architecture.json"));
            Files.copy(metaData.getExperimentDescriptionLocation().toPath(),
                Paths.get(reportLocation.toString(), "experiment.json"));
            JarUtil.copyFolderFromJar("Report", reportLocation.toFile(), StandardCopyOption.REPLACE_EXISTING);

        } catch (SecurityException e) {
            System.out.printf("[Error] No access to report location %s possible%n", reportLocationBaseDirectory);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return reportLocation;
    }
}