package cambio.simulator.entities.networking;

import java.util.Objects;

import cambio.simulator.entities.NamedEntity;
import cambio.simulator.entities.microservice.Microservice;
import cambio.simulator.entities.microservice.MicroserviceInstance;
import cambio.simulator.entities.microservice.Operation;
import desmoj.core.simulator.Model;

/**
 * This is an instance of a {@link DependencyDescription}. It describes an actual existing dependency of a {@code
 * Request}, that arrived at a {@code MicroserviceInstance}.
 *
 * @author Lion Wagner
 * @see DependencyDescription
 * @see Request
 * @see MicroserviceInstance
 */
public class NetworkDependency extends NamedEntity {

    private final Request parentRequest;
    private final Microservice targetMicroservice;
    private final Operation targetOp;
    private final DependencyDescription dependencyDescription;
    private boolean completed;
    private Request childRequest;

    /**
     * Creates an actual instance of a {@link DependencyDescription}.
     *
     * @param model                 DESMO-J model
     * @param parentRequest         {@link Request} that requires this dependency.
     * @param targetOp              {@link Operation} that is targeted by this dependency.
     * @param dependencyDescription generic data that describes this dependency.
     */
    public NetworkDependency(Model model, Request parentRequest, Operation targetOp,
                             DependencyDescription dependencyDescription) {
        super(model, String.format("Dependency(%s)of[%s]", targetOp.getPlainName(), parentRequest.getPlainName()),
            false);
        this.parentRequest = parentRequest;
        this.targetOp = targetOp;
        this.targetMicroservice = targetOp.getOwnerMS();
        this.dependencyDescription = dependencyDescription;
    }


    public Request getParentRequest() {
        return parentRequest;
    }

    public Microservice getTargetService() {
        return targetMicroservice;
    }

    public Operation getTargetOp() {
        return targetOp;
    }

    public boolean isCompleted() {
        return completed;
    }

    void setCompleted() {
        this.completed = true;
    }

    public Request getChildRequest() {
        return childRequest;
    }

    /**
     * This method is used to overwrite the child request that was used to try to complete this dependency. For example,
     * if a retry creates a new request because the previous one timed out, it has to notify (update) the {@code
     * NetworkDependency} that a new child request for this dependency was created.
     *
     * @param childRequest new child request that overwrites the current one
     */
    public void updateChildRequest(Request childRequest) {
        this.childRequest = childRequest;
        //TODO: log
    }

    public double getNextExtraDelay() {
        return dependencyDescription.getNextExtraDelay();
    }

    public boolean hasCustomDelay() {
        return dependencyDescription.hasCustomDelay();
    }

    public double getNextCustomDelay() {
        return dependencyDescription.getNextCustomDelay();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        NetworkDependency that = (NetworkDependency) other;
        return parentRequest.equals(that.parentRequest) && targetOp.equals(that.targetOp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(parentRequest, targetOp);
    }
}
