import java.io.*;
import java.util.*;
public class SetCoverSolver {

    private static class SetDesc {
        int index;
        int cost;
        int[] elems;
    }

    public static void main(String[] args) throws Exception {
        BufferedReader br;
        if (args.length > 0) {
            br = new BufferedReader(new FileReader(args[0]));
        } else {
            br = new BufferedReader(new InputStreamReader(System.in));
        }
        long cost = solve(br);
        System.out.println(cost);
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
        if (first.length < 2) {
            throw new IllegalArgumentException("First line must contain n and m");
        }

        int n = Integer.parseInt(first[0]);
        int m = Integer.parseInt(first[1]);

        SetDesc[] sets = new SetDesc[m];

        int readSets = 0;
        while (readSets < m && (line = br.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] parts = line.split("\\s+");
            if (parts.length < 2) {
                continue;
            }

            int cost = Integer.parseInt(parts[0]);
            int[] elems = new int[parts.length - 1];
            for (int j = 1; j < parts.length; j++) {
                elems[j - 1] = Integer.parseInt(parts[j]);
            }

            SetDesc s = new SetDesc();
            s.index = readSets;
            s.cost = cost;
            s.elems = elems;

            sets[readSets] = s;
            readSets++;
        }

        Result best = solveWithRestarts(sets, n);
        return best.totalCost;
    }

    private static class Result {
        boolean[] usedSets;
        long totalCost;
        boolean allCovered;
    }

    private static Result solveWithRestarts(SetDesc[] sets, int n) {
        int m = sets.length;
        Random rnd = new Random(123456L);
        Result best = null;

        final int ITER = 32;

        for (int it = 0; it < ITER; it++) {
            boolean randomized = (it > 0);
            Result cur = runGreedy(sets, n, rnd, randomized);
            if (!cur.allCovered) {
                continue;
            }
            if (best == null || cur.totalCost < best.totalCost) {
                Result stored = new Result();
                stored.allCovered = cur.allCovered;
                stored.totalCost = cur.totalCost;
                stored.usedSets = Arrays.copyOf(cur.usedSets, m);
                best = stored;
            }
        }

        if (best == null) {
            best = new Result();
            best.usedSets = new boolean[sets.length];
            best.totalCost = -1L;
            best.allCovered = false;
        }

        return best;
    }

    private static Result runGreedy(SetDesc[] sets, int n, Random rnd, boolean randomized) {
        int m = sets.length;
        boolean[] covered = new boolean[n];
        boolean[] usedSet = new boolean[m];
        int coveredCount = 0;
        long totalCost = 0L;

        int[] order = randomized ? new int[m] : null;

        while (coveredCount < n) {
            int bestSet = -1;
            double bestRatio = Double.POSITIVE_INFINITY;

            if (randomized) {
                for (int i = 0; i < m; i++) {
                    order[i] = i;
                }
                for (int i = m - 1; i > 0; i--) {
                    int j = rnd.nextInt(i + 1);
                    int tmp = order[i];
                    order[i] = order[j];
                    order[j] = tmp;
                }

                for (int idx = 0; idx < m; idx++) {
                    int i = order[idx];
                    if (usedSet[i] || sets[i] == null) {
                        continue;
                    }
                    SetDesc s = sets[i];

                    int gain = 0;
                    for (int e : s.elems) {
                        if (e >= 0 && e < n && !covered[e]) {
                            gain++;
                        }
                    }
                    if (gain == 0) {
                        continue;
                    }

                    double ratio = (double) s.cost / gain;
                    ratio *= (1.0 + rnd.nextDouble() * 1e-3);

                    if (ratio < bestRatio) {
                        bestRatio = ratio;
                        bestSet = i;
                    }
                }
            } else {
                for (int i = 0; i < m; i++) {
                    if (usedSet[i] || sets[i] == null) {
                        continue;
                    }
                    SetDesc s = sets[i];

                    int gain = 0;
                    for (int e : s.elems) {
                        if (e >= 0 && e < n && !covered[e]) {
                            gain++;
                        }
                    }
                    if (gain == 0) {
                        continue;
                    }

                    double ratio = (double) s.cost / gain;
                    if (ratio < bestRatio) {
                        bestRatio = ratio;
                        bestSet = i;
                    }
                }
            }

            if (bestSet == -1) {
                break;
            }

            usedSet[bestSet] = true;
            totalCost += sets[bestSet].cost;

            for (int e : sets[bestSet].elems) {
                if (e >= 0 && e < n && !covered[e]) {
                    covered[e] = true;
                    coveredCount++;
                }
            }
        }

        Result res = new Result();
        res.usedSets = usedSet;
        res.totalCost = totalCost;
        res.allCovered = (coveredCount == n);
        return res;
    }
}