package cc.interstellarmc.curvature;

public class LogisticCurve implements ICurve {
    private final double L, k, x0;
    public LogisticCurve(double L, double k, double x0) {
        this.L = L;
        this.k = k;
        this.x0 = x0;
    }
    @Override
    public double apply(double x) {
        return L / (1 + Math.exp(-k * (x - x0)));
    }
}