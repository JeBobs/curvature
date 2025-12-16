package cc.interstellarmc.curvature;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Curve defined by sampled (x,y) points with configurable interpolation.
 */
public class SampledCurve implements ICurve {
    private final List<Point> points; // must be sorted by x ascending
    private final Interpolation interpolation;

    public SampledCurve(List<Point> points, Interpolation interpolation) {
        this.points = points;
        this.points.sort(Comparator.comparingDouble(p -> p.x));
        this.interpolation = interpolation;
    }

    @Override
    public double apply(double x) {
        if (points.isEmpty()) return 0.0;
        if (points.size() == 1) return points.get(0).y;

        Point first = points.get(0);
        Point last = points.get(points.size() - 1);
        if (x <= first.x) return first.y;
        if (x >= last.x) return last.y;

        for (int i = 0; i < points.size() - 1; i++) {
            Point a = points.get(i);
            Point b = points.get(i + 1);
            if (x <= b.x) {
                double t = (x - a.x) / (b.x - a.x);
                return interpolate(a.y, b.y, t);
            }
        }
        return last.y;
    }

    private double interpolate(double y0, double y1, double t) {
        switch (interpolation) {
            case STEP:
                return y0;
            case SMOOTH:
                t = t * t * (3 - 2 * t);
                return y0 + (y1 - y0) * t;
            case LINEAR:
            default:
                return y0 + (y1 - y0) * t;
        }
    }

    public static class Point {
        public final double x;
        public final double y;
        public Point(double x, double y) {
            this.x = x;
            this.y = y;
        }

        public static Point of(Double x, Double y) {
            if (x == null || y == null) return null;
            return new Point(x, y);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Point)) return false;
            Point point = (Point) o;
            return Double.compare(point.x, x) == 0 && Double.compare(point.y, y) == 0;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y);
        }
    }

    public enum Interpolation {
        LINEAR,
        STEP,
        SMOOTH
    }
}
