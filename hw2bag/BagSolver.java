import java.io.*;
import java.util.*;

/**
 * 0/1 knapsack: maximize sum of values with sum of weights &lt;= capacity.
 * Input: first line "n W", then n lines "value weight" (weight sum at most W).
 */
public class BagSolver {

    /** Above this (n * capacity) we skip exact DP and use a simple greedy baseline. */
    private static final long MAX_DP_CELLS = 50_000_000L;

    public static void main(String[] args) throws Exception {
        BufferedReader br;
        if (args.length > 0) {
            br = new BufferedReader(new FileReader(args[0]));
        } else {
            br = new BufferedReader(new InputStreamReader(System.in));
        }
        System.out.println(solve(br));
    }

    public static long solveFromFile(String fileName) throws Exception {
        BufferedReader br = new BufferedReader(new FileReader(fileName));
        return solve(br);
    }

    public static long solve(BufferedReader br) throws IOException {
        String line;
        do {
            line = br.readLine();
            if (line == null) {
                return -1L;
            }
            line = line.trim();
        } while (line.isEmpty());

        String[] first = line.split("\\s+");
        int n = Integer.parseInt(first[0]);
        int capacity = Integer.parseInt(first[1]);

        int[] values = new int[n];
        int[] weights = new int[n];
        int idx = 0;
        while (idx < n && (line = br.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] parts = line.split("\\s+");
            values[idx] = Integer.parseInt(parts[0]);
            weights[idx] = Integer.parseInt(parts[1]);
            idx++;
        }

        long cells = (long) n * capacity;
        if (n > 0 && cells <= MAX_DP_CELLS) {
            return solveDp01(values, weights, n, capacity);
        }
        return solveGreedyByDensity(values, weights, n, capacity);
    }

    /** Exact 0/1 knapsack via one-dimensional DP, O(n * W). */
    private static long solveDp01(int[] v, int[] w, int n, int W) {
        int[] dp = new int[W + 1];
        for (int i = 0; i < n; i++) {
            int wi = w[i];
            int vi = v[i];
            for (int cap = W; cap >= wi; cap--) {
                int cand = dp[cap - wi] + vi;
                if (cand > dp[cap]) {
                    dp[cap] = cand;
                }
            }
        }
        return dp[W];
    }

    /**
     * Primitive baseline: sort by value/weight descending, take items while they fit.
     * Fast; not optimal in general — intended as a hook for heuristics / local search.
     */
    private static long solveGreedyByDensity(int[] v, int[] w, int n, int W) {
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        Arrays.sort(order, (a, b) -> {
            long lhs = (long) v[a] * w[b];
            long rhs = (long) v[b] * w[a];
            return Long.compare(rhs, lhs);
        });
        long totalValue = 0;
        int used = 0;
        for (int k = 0; k < n; k++) {
            int i = order[k];
            if (w[i] <= W - used) {
                used += w[i];
                totalValue += v[i];
            }
        }
        return totalValue;
    }
}
