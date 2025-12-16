package cc.interstellarmc.curvature;

public class BoundedCurve implements ICurve {
    private final ICurve delegate;
    private final double minY;
    private final double maxY;

    public BoundedCurve(ICurve delegate, double minY, double maxY) {
        this.delegate = delegate;
        this.minY = minY;
        this.maxY = maxY;
    }

    @Override
    public double apply(double x) {
        double y = delegate.apply(x);
        if (y < minY) return minY;
        if (y > maxY) return maxY;
        return y;
    }
}