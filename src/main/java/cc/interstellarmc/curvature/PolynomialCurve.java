package cc.interstellarmc.curvature;

public class PolynomialCurve implements ICurve {
    private final double a, b, c;
    public PolynomialCurve(double a, double b, double c) {
        this.a = a;
        this.b = b;
        this.c = c;
    }
    @Override
    public double apply(double x) {
        return a * x * x + b * x + c;
    }
}