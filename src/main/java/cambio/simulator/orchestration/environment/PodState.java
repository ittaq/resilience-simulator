package cambio.simulator.orchestration.environment;

public enum PodState {
    PENDING,
    RUNNING,
    SUCCEEDED,
    PRETERMINATING,
    TERMINATING,
    FAILED,
    UNKNOWN
}
