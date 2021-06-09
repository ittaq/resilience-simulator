package de.rss.fachstudie.MiSim.entities.patterns;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import de.rss.fachstudie.MiSim.entities.microservice.Microservice;
import de.rss.fachstudie.MiSim.entities.microservice.MicroserviceInstance;
import de.rss.fachstudie.MiSim.entities.networking.IRequestUpdateListener;
import de.rss.fachstudie.MiSim.entities.networking.InternalRequest;
import de.rss.fachstudie.MiSim.entities.networking.NetworkDependency;
import de.rss.fachstudie.MiSim.entities.networking.NetworkRequestCanceledEvent;
import de.rss.fachstudie.MiSim.entities.networking.NetworkRequestEvent;
import de.rss.fachstudie.MiSim.entities.networking.Request;
import de.rss.fachstudie.MiSim.entities.networking.RequestFailedReason;
import de.rss.fachstudie.MiSim.export.MultiDataPointReporter;
import de.rss.fachstudie.MiSim.misc.Priority;
import de.rss.fachstudie.MiSim.parsing.FromJson;
import desmoj.core.simulator.Model;
import desmoj.core.simulator.TimeInstant;

/**
 * Manager class of all CircuitBreakers of one Microservice Instance.
 *
 * <p>
 * Creates a {@code CircuitBreakerState} for each connection to another {@code Microservice} of the owning {@code
 * MicroserviceInstance}
 *
 * <p>
 * This class is a {@code NetworkPattern} and therefore monitors all requests send by its owning {@code
 * MicroserviceInstance}.
 *
 * @author Lion Wagner
 * @see CircuitBreakerState
 * @see Microservice
 * @see MicroserviceInstance
 */
public final class CircuitBreaker extends NetworkPattern implements IRequestUpdateListener {

    @FromJson
    @SuppressWarnings("FieldMayBeFinal")
    private int requestVolumeThreshold = Integer.MAX_VALUE;
    @FromJson
    @SuppressWarnings("FieldMayBeFinal")
    private double errorThresholdPercentage = Double.POSITIVE_INFINITY;
    @FromJson
    @SuppressWarnings("FieldMayBeFinal")
    private double sleepWindow = 0.500;
    @FromJson
    @SuppressWarnings("FieldMayBeFinal")
    private int timeout = Integer.MAX_VALUE;
    @FromJson
    @SuppressWarnings("FieldMayBeFinal")
    private int rollingWindow = 20; //window over which error rates are collected

    private final Set<NetworkDependency> activeConnections = new HashSet<>();
    private final Map<Microservice, CircuitBreakerState> breakerStates = new HashMap<>();
    private final Map<Microservice, Integer> activeConnectionCount = new HashMap<>();
    private final MultiDataPointReporter reporter;


    public CircuitBreaker(Model model, String name, boolean showInTrace, MicroserviceInstance owner) {
        super(model, name, showInTrace, owner);
        reporter = new MultiDataPointReporter(String.format("CB[%s]_", name));
    }

    @Override
    public int getListeningPriority() {
        return Priority.HIGH;
    }

    @Override
    public void shutdown() {
        activeConnections.clear();
        activeConnectionCount.clear();
        breakerStates.clear();
        collectData(presentTime());
    }

    @Override
    public boolean onRequestSend(Request request, TimeInstant when) {
        if (!(request instanceof InternalRequest)) {
            return false; //ignore everything except InternalRequests (e.g. RequestAnswers)
        }

        NetworkDependency dep = request.getParent().getRelatedDependency(request);
        Microservice target = dep.getTargetService();
        activeConnections.add(dep);
        activeConnectionCount.merge(target, 1, Integer::sum);
        CircuitBreakerState state = breakerStates.computeIfAbsent(target,
            monitoredService -> new CircuitBreakerState(monitoredService, this.errorThresholdPercentage, rollingWindow,
                sleepWindow));


        boolean consumed = false;
        if (state.isOpen()) {
            //owner.updateListenerProxy.onRequestFailed(request, when, RequestFailedReason.CIRCUIT_IS_OPEN);
            request.cancelSending();
            NetworkRequestEvent cancelEvent =
                new NetworkRequestCanceledEvent(getModel(), String.format("Canceling of %s", request.getQuotedName()),
                    true, request, RequestFailedReason.CIRCUIT_IS_OPEN);
            cancelEvent.schedule();
            consumed = true;
        } else {
            int currentActiveConnections = activeConnectionCount.get(target);
            if (currentActiveConnections > requestVolumeThreshold) {
                state.notifyArrivalFailure();
                owner.updateListenerProxy
                    .onRequestFailed(request, when, RequestFailedReason.CONNECTION_VOLUME_LIMIT_REACHED);
                consumed = true;
            }
        }
        collectData(when);
        return consumed;
    }

    @Override
    public boolean onRequestArrivalAtTarget(Request request, TimeInstant when) {
        collectData(when);
        return false;
    }

    @Override
    public boolean onRequestResultArrivedAtRequester(Request request, TimeInstant when) {
        if (!(request instanceof InternalRequest)) {
            return false; //ignore everything except InternalRequests (e.g. RequestAnswers)
        }

        NetworkDependency dep = request.getParent().getRelatedDependency(request);
        Microservice target = dep.getTargetService();

        if (target == this.owner.getOwner()) { //prevents the circuit breaker from reacting to unpacked RequestAnswers
            return false;
        }

        activeConnections.remove(dep);
        activeConnectionCount.merge(target, -1, Integer::sum);

        breakerStates.get(target).notifySuccessfulCompletion();

        collectData(when);
        return false;
    }

    @Override
    public boolean onRequestFailed(Request request, TimeInstant when, RequestFailedReason reason) {
        if (!(request instanceof InternalRequest)) {
            return false; //ignore everything except InternalRequests (e.g. RequestAnswers)
        }

        InternalRequest internalRequest = (InternalRequest) request;

        NetworkDependency dep = internalRequest.getDependency();
        if (dep.getChildRequest() != internalRequest) {
            //dependency was asinged a new child Request already (e.g due to a retry), therefore we ignore the request
            return false;
        }

        Microservice target = dep.getTargetService();
        if (activeConnections.remove(dep)) {
            breakerStates.get(target).notifyArrivalFailure();
        }

        collectData(when);
        return false;
    }


    private void collectData(TimeInstant when) {
        for (Map.Entry<Microservice, CircuitBreakerState> entry : breakerStates.entrySet()) {
            Microservice microservice = entry.getKey();
            CircuitBreakerState circuitBreakerState = entry.getValue();
            reporter.addDatapoint(String.format("[%s]", microservice.getName()), when,
                circuitBreakerState.getCurrentStatistics());
        }
    }
}
