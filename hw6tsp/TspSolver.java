import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;

/**
 * Multistart nearest neighbor, keep top-K tours, 2-opt + 3-opt on each candidate.
 */
public class TspSolver {

    private static final int TOP_K = 5;
    private static final int FULL_MULTISTART_N = 600;
    /** Full 2-opt/3-opt only up to this n; larger instances use sampled local search. */
    private static final int LOCAL_SEARCH_EXHAUSTIVE_MAX_N = 1_000;
    private static final int TWO_OPT_MAX_EXHAUSTIVE_PASSES = 50_000;
    private static final int THREE_OPT_MAX_EXHAUSTIVE_PASSES = 50_000;
    private static final int TWO_OPT_RANDOM_ROUNDS = 16;
    private static final int TWO_OPT_SAMPLES_PER_ROUND = 800_000;
    private static final int THREE_OPT_RANDOM_ROUNDS = 48;
    private static final int THREE_OPT_SAMPLES_PER_ROUND = 1_200_000;

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
        double[] topLen = new double[TOP_K];
        int[][] topTours = new int[TOP_K][];
        int topCount = 0;

        for (int t = 0; t < numStarts; t++) {
            int start = startVertex(n, numStarts, t);
            int[] tour = nearestNeighborTour(x, y, n, start);
            double len = tourLength(tour, x, y, n);
            topCount = insertTopTour(topTours, topLen, topCount, TOP_K, tour, len, n);
        }

        int[] bestTour = Arrays.copyOf(topTours[0], n);
        double bestLen = topLen[0];
        for (int k = 0; k < topCount; k++) {
            int[] tour = Arrays.copyOf(topTours[k], n);
            localSearch(tour, x, y, n);
            double len = tourLength(tour, x, y, n);
            if (len < bestLen) {
                bestLen = len;
                bestTour = tour;
            }
        }

        return Math.round(tourLength(bestTour, x, y, n));
    }

    private static int insertTopTour(int[][] topTours, double[] topLen, int topCount, int k,
            int[] tour, double len, int n) {
        if (topCount < k) {
            topTours[topCount] = Arrays.copyOf(tour, n);
            topLen[topCount] = len;
            return topCount + 1;
        }
        int worst = 0;
        for (int i = 1; i < k; i++) {
            if (topLen[i] > topLen[worst]) {
                worst = i;
            }
        }
        if (len < topLen[worst]) {
            topTours[worst] = Arrays.copyOf(tour, n);
            topLen[worst] = len;
        }
        return k;
    }

    private static int multistartCount(int n) {
        if (n <= FULL_MULTISTART_N) {
            return n;
        }
        if (n <= 2000) {
            return Math.min(n, 150);
        }
        if (n <= 12000) {
            return Math.min(n, 32);
        }
        return Math.min(n, 8);
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

    private static int[] nearestNeighborTour(double[] x, double[] y, int n, int start) {
        boolean[] used = new boolean[n];
        int[] tour = new int[n];
        tour[0] = start;
        used[start] = true;
        int cur = start;
        for (int step = 1; step < n; step++) {
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
            tour[step] = next;
            used[next] = true;
            cur = next;
        }
        return tour;
    }

    private static void localSearch(int[] tour, double[] x, double[] y, int n) {
        if (n < 4) {
            return;
        }
        twoOpt(tour, x, y, n);
        threeOpt(tour, x, y, n);
        twoOpt(tour, x, y, n);
    }

    private static double tourLength(int[] tour, double[] x, double[] y, int n) {
        double s = dist(x, y, tour[n - 1], tour[0]);
        for (int i = 0; i < n - 1; i++) {
            s += dist(x, y, tour[i], tour[i + 1]);
        }
        return s;
    }

    private static double minGainThreshold(double lenTour) {
        return Math.max(1e-9, 1e-12 * lenTour);
    }

    private static void twoOpt(int[] tour, double[] x, double[] y, int n) {
        if (n < 4) {
            return;
        }
        if (n <= LOCAL_SEARCH_EXHAUSTIVE_MAX_N) {
            for (int pass = 0; pass < TWO_OPT_MAX_EXHAUSTIVE_PASSES; pass++) {
                if (!twoOptExhaustiveRound(tour, x, y, n)) {
                    break;
                }
            }
        } else {
            Random rnd = new Random(131313L);
            for (int r = 0; r < TWO_OPT_RANDOM_ROUNDS; r++) {
                if (!twoOptRandomRound(tour, x, y, n, rnd, TWO_OPT_SAMPLES_PER_ROUND)) {
                    break;
                }
            }
        }
    }

    private static boolean twoOptExhaustiveRound(int[] tour, double[] x, double[] y, int n) {
        double lenTour = tourLength(tour, x, y, n);
        double minGain = minGainThreshold(lenTour);

        int[] dup = new int[2 * n];
        System.arraycopy(tour, 0, dup, 0, n);
        System.arraycopy(tour, 0, dup, n, n);

        double bestDelta = 0.0;
        int bestI = -1;
        int bestJ = -1;

        for (int i = 0; i < n; i++) {
            int a = dup[i];
            int b = dup[i + 1];
            for (int j = i + 2; j <= i + n - 2; j++) {
                int c = dup[j];
                int d = dup[j + 1];
                double delta = dist(x, y, a, c) + dist(x, y, b, d) - dist(x, y, a, b) - dist(x, y, c, d);
                if (delta < -minGain && delta < bestDelta - 1e-15) {
                    bestDelta = delta;
                    bestI = i;
                    bestJ = j;
                }
            }
        }

        if (bestI < 0 || bestDelta >= -minGain) {
            return false;
        }

        double beforeLen = lenTour;
        int[] backup = Arrays.copyOf(tour, n);
        applyTwoOpt(tour, n, bestI, bestJ);
        if (tourLength(tour, x, y, n) >= beforeLen - minGain) {
            System.arraycopy(backup, 0, tour, 0, n);
            return false;
        }
        return true;
    }

    private static boolean twoOptRandomRound(int[] tour, double[] x, double[] y, int n, Random rnd, int samples) {
        double lenTour0 = tourLength(tour, x, y, n);
        double minGain0 = minGainThreshold(lenTour0);

        int[] dup = new int[2 * n];
        for (int s = 0; s < samples; s++) {
            System.arraycopy(tour, 0, dup, 0, n);
            System.arraycopy(tour, 0, dup, n, n);
            int i = rnd.nextInt(n);
            int j = i + 2 + rnd.nextInt(Math.max(1, n - 4));
            if (j > i + n - 2) {
                continue;
            }

            int a = dup[i];
            int b = dup[i + 1];
            int c = dup[j];
            int d = dup[j + 1];
            double delta = dist(x, y, a, c) + dist(x, y, b, d) - dist(x, y, a, b) - dist(x, y, c, d);
            if (delta >= -minGain0) {
                continue;
            }

            int[] backup = Arrays.copyOf(tour, n);
            applyTwoOpt(tour, n, i, j);
            if (tourLength(tour, x, y, n) < lenTour0 - minGain0) {
                return true;
            }
            System.arraycopy(backup, 0, tour, 0, n);
        }
        return false;
    }

    private static void applyTwoOpt(int[] tour, int n, int i, int j) {
        int[] work = new int[n];
        for (int p = 0; p < n; p++) {
            work[p] = tour[(i + p) % n];
        }
        reverse(work, 1, j - i);
        for (int p = 0; p < n; p++) {
            tour[(i + p) % n] = work[p];
        }
    }

    private static void threeOpt(int[] tour, double[] x, double[] y, int n) {
        if (n < 6) {
            return;
        }
        if (n <= LOCAL_SEARCH_EXHAUSTIVE_MAX_N) {
            for (int pass = 0; pass < THREE_OPT_MAX_EXHAUSTIVE_PASSES; pass++) {
                if (!threeOptExhaustiveRound(tour, x, y, n)) {
                    break;
                }
            }
        } else {
            Random rnd = new Random(424242L);
            for (int r = 0; r < THREE_OPT_RANDOM_ROUNDS; r++) {
                if (!threeOptRandomRound(tour, x, y, n, rnd, THREE_OPT_SAMPLES_PER_ROUND)) {
                    break;
                }
            }
        }
    }

    private static boolean threeOptExhaustiveRound(int[] tour, double[] x, double[] y, int n) {
        double lenTour = tourLength(tour, x, y, n);
        double minGain = minGainThreshold(lenTour);

        int[] dup = new int[2 * n];
        System.arraycopy(tour, 0, dup, 0, n);
        System.arraycopy(tour, 0, dup, n, n);

        double bestDelta = 0.0;
        int bestI = -1;
        int bestJ = -1;
        int bestK = -1;
        int bestCase = -1;

        for (int i = 0; i < n; i++) {
            int a = dup[i];
            int b = dup[i + 1];
            for (int j = i + 2; j <= i + n - 2; j++) {
                int c = dup[j];
                int d = dup[j + 1];
                for (int k = j + 2; k <= i + n - 2; k++) {
                    int e = dup[k];
                    int f = dup[k + 1];

                    double d0 = dist(x, y, a, b) + dist(x, y, c, d) + dist(x, y, e, f);

                    double g2 = dist(x, y, a, c) + dist(x, y, b, d) + dist(x, y, e, f) - d0;
                    double g3 = dist(x, y, a, b) + dist(x, y, c, e) + dist(x, y, d, f) - d0;
                    double g4 = dist(x, y, a, d) + dist(x, y, e, b) + dist(x, y, c, f) - d0;
                    double g5 = dist(x, y, a, e) + dist(x, y, c, b) + dist(x, y, d, f) - d0;
                    double g6 = dist(x, y, a, d) + dist(x, y, e, c) + dist(x, y, b, f) - d0;
                    double g7 = dist(x, y, a, e) + dist(x, y, d, b) + dist(x, y, c, f) - d0;

                    if (g2 < -minGain && g2 < bestDelta - 1e-15) {
                        bestDelta = g2;
                        bestI = i;
                        bestJ = j;
                        bestK = k;
                        bestCase = 2;
                    }
                    if (g3 < -minGain && g3 < bestDelta - 1e-15) {
                        bestDelta = g3;
                        bestI = i;
                        bestJ = j;
                        bestK = k;
                        bestCase = 3;
                    }
                    if (g4 < -minGain && g4 < bestDelta - 1e-15) {
                        bestDelta = g4;
                        bestI = i;
                        bestJ = j;
                        bestK = k;
                        bestCase = 4;
                    }
                    if (g5 < -minGain && g5 < bestDelta - 1e-15) {
                        bestDelta = g5;
                        bestI = i;
                        bestJ = j;
                        bestK = k;
                        bestCase = 5;
                    }
                    if (g6 < -minGain && g6 < bestDelta - 1e-15) {
                        bestDelta = g6;
                        bestI = i;
                        bestJ = j;
                        bestK = k;
                        bestCase = 6;
                    }
                    if (g7 < -minGain && g7 < bestDelta - 1e-15) {
                        bestDelta = g7;
                        bestI = i;
                        bestJ = j;
                        bestK = k;
                        bestCase = 7;
                    }
                }
            }
        }

        if (bestCase < 0 || bestDelta >= -minGain) {
            return false;
        }

        double beforeLen = lenTour;
        int[] backup = Arrays.copyOf(tour, n);
        applyThreeOptCase(tour, n, bestI, bestJ, bestK, bestCase);
        if (tourLength(tour, x, y, n) >= beforeLen - minGain) {
            System.arraycopy(backup, 0, tour, 0, n);
            return false;
        }
        return true;
    }

    private static boolean threeOptRandomRound(int[] tour, double[] x, double[] y, int n, Random rnd, int samples) {
        double lenTour0 = tourLength(tour, x, y, n);
        double minGain0 = minGainThreshold(lenTour0);

        int[] dup = new int[2 * n];
        for (int s = 0; s < samples; s++) {
            System.arraycopy(tour, 0, dup, 0, n);
            System.arraycopy(tour, 0, dup, n, n);
            int i = rnd.nextInt(n);
            int j = i + 2 + rnd.nextInt(Math.max(1, n - 4));
            int maxK = i + n - 2;
            if (j + 2 > maxK) {
                continue;
            }
            int k = j + 2 + rnd.nextInt(maxK - (j + 2) + 1);

            int a = dup[i];
            int b = dup[i + 1];
            int c = dup[j];
            int d = dup[j + 1];
            int e = dup[k];
            int f = dup[k + 1];

            double d0 = dist(x, y, a, b) + dist(x, y, c, d) + dist(x, y, e, f);

            double g2 = dist(x, y, a, c) + dist(x, y, b, d) + dist(x, y, e, f) - d0;
            double g3 = dist(x, y, a, b) + dist(x, y, c, e) + dist(x, y, d, f) - d0;
            double g4 = dist(x, y, a, d) + dist(x, y, e, b) + dist(x, y, c, f) - d0;
            double g5 = dist(x, y, a, e) + dist(x, y, c, b) + dist(x, y, d, f) - d0;
            double g6 = dist(x, y, a, d) + dist(x, y, e, c) + dist(x, y, b, f) - d0;
            double g7 = dist(x, y, a, e) + dist(x, y, d, b) + dist(x, y, c, f) - d0;

            int bestCase = -1;
            double bestG = 0.0;
            if (g2 < -minGain0 && g2 < bestG - 1e-15) {
                bestG = g2;
                bestCase = 2;
            }
            if (g3 < -minGain0 && g3 < bestG - 1e-15) {
                bestG = g3;
                bestCase = 3;
            }
            if (g4 < -minGain0 && g4 < bestG - 1e-15) {
                bestG = g4;
                bestCase = 4;
            }
            if (g5 < -minGain0 && g5 < bestG - 1e-15) {
                bestG = g5;
                bestCase = 5;
            }
            if (g6 < -minGain0 && g6 < bestG - 1e-15) {
                bestG = g6;
                bestCase = 6;
            }
            if (g7 < -minGain0 && g7 < bestG - 1e-15) {
                bestG = g7;
                bestCase = 7;
            }

            if (bestCase >= 0) {
                double beforeLen = lenTour0;
                int[] backup = Arrays.copyOf(tour, n);
                applyThreeOptCase(tour, n, i, j, k, bestCase);
                if (tourLength(tour, x, y, n) < beforeLen - minGain0) {
                    return true;
                }
                System.arraycopy(backup, 0, tour, 0, n);
            }
        }
        return false;
    }

    private static void applyThreeOptCase(int[] tour, int n, int i, int j, int k, int caseId) {
        int[] work = new int[n];
        for (int p = 0; p < n; p++) {
            work[p] = tour[(i + p) % n];
        }
        int pj = j - i;
        int pk = k - i;

        switch (caseId) {
            case 2:
                reverse(work, 1, pj);
                break;
            case 3:
                reverse(work, pj + 1, pk);
                break;
            case 4:
                reverse(work, 1, pk);
                break;
            case 5:
                reverse(work, 1, pj);
                reverse(work, pj + 1, pk);
                break;
            case 6:
                reverse(work, pj + 1, pk);
                reverse(work, 1, pk);
                break;
            case 7:
                reverse(work, pj + 1, pk);
                reverse(work, 1, pj);
                break;
            default:
                break;
        }

        for (int p = 0; p < n; p++) {
            tour[(i + p) % n] = work[p];
        }
    }

    private static void reverse(int[] arr, int lo, int hi) {
        while (lo < hi) {
            int t = arr[lo];
            arr[lo] = arr[hi];
            arr[hi] = t;
            lo++;
            hi--;
        }
    }

    private static double dist(double[] x, double[] y, int a, int b) {
        return euclid(x[a], y[a], x[b], y[b]);
    }

    private static double euclid(double x1, double y1, double x2, double y2) {
        double dx = x1 - x2;
        double dy = y1 - y2;
        return Math.hypot(dx, dy);
    }
}
