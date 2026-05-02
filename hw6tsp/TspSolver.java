import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;


public class TspSolver {

    private static final int FULL_MULTISTART_N = 520;

    public static long solveFromFile(String fileName) throws Exception {
        try (BufferedReader br = new BufferedReader(new FileReader(fileName))) {
            return solve(br);
        }
    }

    public static long solve(BufferedReader br) throws IOException {
        String line = br.readLine();
        if (line == null) {
            return -1L;
        }
        int n = Integer.parseInt(line.trim());
        double[] x = new double[n];
        double[] y = new double[n];
        for (int i = 0; i < n; i++) {
            line = br.readLine();
            if (line == null) {
                return -1L;
            }
            String[] parts = line.trim().split("\\s+");
            x[i] = Double.parseDouble(parts[0]);
            y[i] = Double.parseDouble(parts[1]);
        }

        if (n <= 1) {
            return 0L;
        }

        int numStarts = multistartCount(n);
        double best = Double.POSITIVE_INFINITY;
        for (int t = 0; t < numStarts; t++) {
            int start = startVertex(n, numStarts, t);
            double len = nearestNeighborTourLength(x, y, n, start);
            if (len < best) {
                best = len;
            }
        }
        return Math.round(best);
    }

    private static int multistartCount(int n) {
        if (n <= FULL_MULTISTART_N) {
            return n;
        }
        if (n <= 2000) {
            return Math.min(n, 72);
        }
        if (n <= 12000) {
            return Math.min(n, 24);
        }
        return Math.min(n, 6);
    }

    private static int startVertex(int n, int numStarts, int idx) {
        if (numStarts >= n) {
            return idx;
        }
        if (numStarts <= 1) {
            return 0;
        }
        return (int) ((long) idx * (n - 1) / (numStarts - 1));
    }

    private static double nearestNeighborTourLength(double[] x, double[] y, int n, int start) {
        boolean[] used = new boolean[n];
        int cur = start;
        used[start] = true;
        double sum = 0.0;
        for (int k = 1; k < n; k++) {
            int next = -1;
            double bestD = Double.POSITIVE_INFINITY;
            for (int j = 0; j < n; j++) {
                if (used[j]) {
                    continue;
                }
                double d = euclid(x[cur], y[cur], x[j], y[j]);
                if (d < bestD) {
                    bestD = d;
                    next = j;
                }
            }
            sum += bestD;
            used[next] = true;
            cur = next;
        }
        sum += euclid(x[cur], y[cur], x[start], y[start]);
        return sum;
    }

    private static double euclid(double x1, double y1, double x2, double y2) {
        double dx = x1 - x2;
        double dy = y1 - y2;
        return Math.hypot(dx, dy);
    }
}
