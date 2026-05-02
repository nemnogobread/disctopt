import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

/**
 * Euclidean TSP: nearest-neighbor tour from vertex 0 (greedy, no local search).
 */
public class TspSolver {

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

        boolean[] used = new boolean[n];
        int cur = 0;
        used[0] = true;
        double sum = 0.0;
        for (int k = 1; k < n; k++) {
            int next = -1;
            double best = Double.POSITIVE_INFINITY;
            for (int j = 0; j < n; j++) {
                if (used[j]) {
                    continue;
                }
                double d = euclid(x[cur], y[cur], x[j], y[j]);
                if (d < best) {
                    best = d;
                    next = j;
                }
            }
            sum += best;
            used[next] = true;
            cur = next;
        }
        sum += euclid(x[cur], y[cur], x[0], y[0]);
        return Math.round(sum);
    }

    private static double euclid(double x1, double y1, double x2, double y2) {
        double dx = x1 - x2;
        double dy = y1 - y2;
        return Math.hypot(dx, dy);
    }
}
