import java.io.*;
import java.util.*;


public class BagSolver {

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
        return solveGreedyWithLocalSearch(values, weights, n, capacity);
    }

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


    private static long solveGreedyWithLocalSearch(int[] v, int[] w, int n, int W) {
        boolean[] take = new boolean[n];
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        Arrays.sort(order, (a, b) -> {
            long lhs = (long) v[a] * w[b];
            long rhs = (long) v[b] * w[a];
            return Long.compare(rhs, lhs);
        });
        int curW = 0;
        for (int k = 0; k < n; k++) {
            int i = order[k];
            if (w[i] <= W - curW) {
                take[i] = true;
                curW += w[i];
            }
        }
        return localSearchHillClimb(v, w, n, W, take);
    }

    private static long localSearchHillClimb(int[] v, int[] w, int n, int W, boolean[] take) {
        int curW = 0;
        long curV = 0;
        for (int i = 0; i < n; i++) {
            if (take[i]) {
                curW += w[i];
                curV += v[i];
            }
        }
        while (true) {
            int bestJ = -1;
            int bestAddVal = -1;
            for (int j = 0; j < n; j++) {
                if (!take[j] && curW + w[j] <= W) {
                    if (v[j] > bestAddVal) {
                        bestAddVal = v[j];
                        bestJ = j;
                    }
                }
            }
            int bestI = -1;
            int bestJswap = -1;
            int bestDelta = 0;
            for (int i = 0; i < n; i++) {
                if (!take[i]) {
                    continue;
                }
                for (int j = 0; j < n; j++) {
                    if (take[j]) {
                        continue;
                    }
                    if (curW - w[i] + w[j] <= W) {
                        int d = v[j] - v[i];
                        if (d > bestDelta) {
                            bestDelta = d;
                            bestI = i;
                            bestJswap = j;
                        }
                    }
                }
            }
            boolean canAdd = bestJ >= 0 && bestAddVal > 0;
            boolean canSwap = bestDelta > 0;
            if (!canAdd && !canSwap) {
                break;
            }
            if (canAdd && (!canSwap || bestAddVal >= bestDelta)) {
                take[bestJ] = true;
                curW += w[bestJ];
                curV += bestAddVal;
            } else {
                take[bestI] = false;
                take[bestJswap] = true;
                curW += w[bestJswap] - w[bestI];
                curV += bestDelta;
            }
        }
        return curV;
    }
}
