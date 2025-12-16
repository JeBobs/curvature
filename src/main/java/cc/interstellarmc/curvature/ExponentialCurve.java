package cc.interstellarmc.curvature;

public class ExponentialCurve implements ICurve {
    private final double base;
    public ExponentialCurve(double base) {
        this.base = base;
    }
    @Override
    public double apply(double x) {
        return Math.pow(base, x);
    }
}