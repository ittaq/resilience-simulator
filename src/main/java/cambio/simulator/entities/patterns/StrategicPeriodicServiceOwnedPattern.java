package cambio.simulator.entities.patterns;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import desmoj.core.simulator.Model;
import desmoj.core.simulator.TimeInstant;

/**
 * Represents a pattern owned by a {@link cambio.simulator.entities.microservice.Microservice} that employs a {@link
 * IStrategy}. This periodic pattern will automatically and periodically the {@link IPeriodicPattern#trigger()} method.
 *
 * @author Lion Wagner
 * @see IPeriodicPattern
 */
public abstract class StrategicPeriodicServiceOwnedPattern<S extends IStrategy> extends StrategicServiceOwnedPattern<S>
    implements IPeriodicPattern {

    @Expose
    @SerializedName(value = "interval", alternate = {"period"})
    protected final double period = 1;
    @Expose
    protected final double start = 0;
    @Expose
    protected final double stop = Double.MAX_VALUE;

    private transient PeriodicPatternScheduler scheduler;

    public StrategicPeriodicServiceOwnedPattern(Model model, String name, boolean showInTrace) {
        super(model, name, showInTrace);
    }

    @Override
    public void onInitializedCompleted() {
        scheduler = new PeriodicPatternScheduler(getModel(), this, start, stop, period);
        scheduler.activate(new TimeInstant(start));
    }

    @Override
    public final PeriodicPatternScheduler getScheduler() {
        return scheduler;
    }
}
