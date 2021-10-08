package SolverSSA;

public class IllegalTauLeapPercentageMetric extends Metric<Double, Double> {
    protected int totalCt;
    protected int totalIllegal;

    public IllegalTauLeapPercentageMetric() {
        super("Failed TL Percentage");
        this.shortName = "FTL";
        this.totalCt = 0;
        this.totalIllegal = 0;
    }

    protected void addSample(double currentTime, Double metric) {
    }

    public Double getMetric() {
        return ((double)this.totalIllegal)/((double) this.totalCt);
    }

    public void fromStateMatrix(double t, StateMatrix stateMatrix) {
    }

    public void fromEvent(double t, Event e) {
    }

    public void fromEvent(double t, Event e, int n) {

    }

    public void addIllegal() {
        this.totalIllegal++;
        this.totalCt++;
    }

    public void addSuccessful() {
        this.totalCt++;
    }
}
