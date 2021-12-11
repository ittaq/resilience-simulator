package cambio.simulator.orchestration.events;

import cambio.simulator.misc.Priority;
import cambio.simulator.orchestration.environment.ContainerState;
import cambio.simulator.orchestration.environment.Pod;
import cambio.simulator.orchestration.environment.PodState;
import co.paralleluniverse.fibers.SuspendExecution;
import desmoj.core.simulator.Event;
import desmoj.core.simulator.Model;

public class StartPodEvent extends Event<Pod> {

    public StartPodEvent(Model model, String name, boolean showInTrace) {
        super(model, name, showInTrace);
        this.setSchedulingPriority(Priority.HIGH);
    }

    @Override
    public void eventRoutine(Pod pod) throws SuspendExecution {
        pod.getContainers().forEach(container -> container.setContainerState(ContainerState.RUNNING));
        pod.getContainers().forEach(container -> container.getMicroserviceInstance().start());
        pod.setPodState(PodState.RUNNING);
    }
}
