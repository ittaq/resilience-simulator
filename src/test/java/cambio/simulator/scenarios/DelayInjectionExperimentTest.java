package cambio.simulator.scenarios;

import java.io.File;

import cambio.simulator.models.MainModel;
import cambio.simulator.testutils.FileLoaderUtil;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * @author Lion Wagner
 */
@Disabled
public class DelayInjectionExperimentTest {

    @Test
    void ExampleExecutionRun() {

        File architecture = FileLoaderUtil.loadFromExampleResources("example_architecture_model.json");
        File experiment = FileLoaderUtil.loadFromExampleResources("example_experiment_delayInjection.json");
        String[] args = new String[] {"-a", architecture.getAbsolutePath(), "-e", experiment.getAbsolutePath(), "-d",
            "-p"};
        MainModel.main(args);
    }
}
