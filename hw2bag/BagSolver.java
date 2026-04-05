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
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        Arrays.sort(order, (a, b) -> {
            long lhs = (long) v[a] * w[b];
            long rhs = (long) v[b] * w[a];
            int c = Long.compare(rhs, lhs);
            if (c != 0) {
                return c;
            }
            return Integer.compare(a, b);
        });
        boolean[] take = new boolean[n];
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

    private enum MoveKind { NONE, ADD, SWAP, TWO_OUT_ONE_IN, ONE_OUT_TWO_IN }

    private static final class BestMove {
        MoveKind kind = MoveKind.NONE;
        int a = -1;
        int b = -1;
        int c = -1;
        long gain;
    }

    private static void findBestMove(int[] v, int[] w, int n, int W, boolean[] take, int curW, BestMove out) {
        out.kind = MoveKind.NONE;
        out.a = out.b = out.c = -1;
        long bestGain = 0;

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
        if (bestJ >= 0 && bestAddVal > 0) {
            bestGain = bestAddVal;
            out.kind = MoveKind.ADD;
            out.a = bestJ;
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
        if (bestDelta > bestGain) {
            bestGain = bestDelta;
            out.kind = MoveKind.SWAP;
            out.a = bestI;
            out.b = bestJswap;
        }

        int bi1 = -1;
        int bi2 = -1;
        int bj = -1;
        long g21 = Long.MIN_VALUE / 4;
        for (int i1 = 0; i1 < n; i1++) {
            if (!take[i1]) {
                continue;
            }
            for (int i2 = i1 + 1; i2 < n; i2++) {
                if (!take[i2]) {
                    continue;
                }
                int wrem = curW - w[i1] - w[i2];
                for (int j = 0; j < n; j++) {
                    if (take[j]) {
                        continue;
                    }
                    if (wrem + w[j] <= W) {
                        long g = (long) v[j] - v[i1] - v[i2];
                        if (g > g21) {
                            g21 = g;
                            bi1 = i1;
                            bi2 = i2;
                            bj = j;
                        }
                    }
                }
            }
        }
        if (g21 > bestGain) {
            bestGain = g21;
            out.kind = MoveKind.TWO_OUT_ONE_IN;
            out.a = bi1;
            out.b = bi2;
            out.c = bj;
        }

        int oi = -1;
        int oj1 = -1;
        int oj2 = -1;
        long g12 = Long.MIN_VALUE / 4;
        for (int i = 0; i < n; i++) {
            if (!take[i]) {
                continue;
            }
            int baseW = curW - w[i];
            for (int j1 = 0; j1 < n; j1++) {
                if (take[j1]) {
                    continue;
                }
                for (int j2 = j1 + 1; j2 < n; j2++) {
                    if (take[j2]) {
                        continue;
                    }
                    if (baseW + w[j1] + w[j2] <= W) {
                        long g = (long) v[j1] + v[j2] - v[i];
                        if (g > g12) {
                            g12 = g;
                            oi = i;
                            oj1 = j1;
                            oj2 = j2;
                        }
                    }
                }
            }
        }
        if (g12 > bestGain) {
            bestGain = g12;
            out.kind = MoveKind.ONE_OUT_TWO_IN;
            out.a = oi;
            out.b = oj1;
            out.c = oj2;
        }
        out.gain = bestGain;
    }

    private static void applyMove(boolean[] take, int[] w, int[] v, BestMove m, int[] curWRef, long[] curVRef) {
        int curW = curWRef[0];
        long curV = curVRef[0];
        switch (m.kind) {
            case ADD:
                take[m.a] = true;
                curW += w[m.a];
                curV += v[m.a];
                break;
            case SWAP:
                take[m.a] = false;
                take[m.b] = true;
                curW += w[m.b] - w[m.a];
                curV += (long) v[m.b] - v[m.a];
                break;
            case TWO_OUT_ONE_IN:
                take[m.a] = false;
                take[m.b] = false;
                take[m.c] = true;
                curW += w[m.c] - w[m.a] - w[m.b];
                curV += (long) v[m.c] - v[m.a] - v[m.b];
                break;
            case ONE_OUT_TWO_IN:
                take[m.a] = false;
                take[m.b] = true;
                take[m.c] = true;
                curW += w[m.b] + w[m.c] - w[m.a];
                curV += (long) v[m.b] + v[m.c] - v[m.a];
                break;
            default:
                break;
        }
        curWRef[0] = curW;
        curVRef[0] = curV;
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
        BestMove move = new BestMove();
        int[] cw = new int[]{curW};
        long[] cv = new long[]{curV};
        while (true) {
            findBestMove(v, w, n, W, take, cw[0], move);
            if (move.kind == MoveKind.NONE || move.gain <= 0) {
                break;
            }
            applyMove(take, w, v, move, cw, cv);
        }
        return cv[0];
    }
}
