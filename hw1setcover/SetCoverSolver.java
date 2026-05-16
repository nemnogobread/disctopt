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

        List<Integer>[] elemToSets = buildElemToSets(sets, n);
        Result best = solveWithRestarts(sets, n, elemToSets);
        return best.totalCost;
    }

    @SuppressWarnings("unchecked")
    private static List<Integer>[] buildElemToSets(SetDesc[] sets, int n) {
        List<Integer>[] elemToSets = new List[n];
        for (int e = 0; e < n; e++) {
            elemToSets[e] = new ArrayList<>();
        }
        for (int i = 0; i < sets.length; i++) {
            if (sets[i] == null) {
                continue;
            }
            for (int e : sets[i].elems) {
                if (e >= 0 && e < n) {
                    elemToSets[e].add(i);
                }
            }
        }
        return elemToSets;
    }

    private static class Result {
        boolean[] usedSets;
        long totalCost;
        boolean allCovered;
    }

    private static Result solveWithRestarts(SetDesc[] sets, int n, List<Integer>[] elemToSets) {
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
            localSearchImprove(sets, n, cur, elemToSets);
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

    private static void localSearchImprove(SetDesc[] sets, int n, Result res, List<Integer>[] elemToSets) {
        final int maxRounds = 20;
        for (int round = 0; round < maxRounds; round++) {
            boolean changed = reverseDeleteOnce(sets, n, res);
            changed |= dropAddPasses(sets, n, res, elemToSets);
            if (!changed) {
                break;
            }
        }
        reverseDeleteOnce(sets, n, res);
    }

    private static boolean reverseDeleteOnce(SetDesc[] sets, int n, Result res) {
        int m = sets.length;
        boolean[] used = res.usedSets;
        int[] coverCount = buildCoverCount(sets, n, used);
        boolean changed = false;

        List<Integer> chosen = new ArrayList<>();
        for (int i = 0; i < m; i++) {
            if (used[i]) {
                chosen.add(i);
            }
        }
        chosen.sort((a, b) -> Integer.compare(sets[b].cost, sets[a].cost));

        for (int i : chosen) {
            if (!used[i]) {
                continue;
            }
            boolean removable = true;
            for (int e : sets[i].elems) {
                if (e >= 0 && e < n && coverCount[e] <= 1) {
                    removable = false;
                    break;
                }
            }
            if (!removable) {
                continue;
            }
            used[i] = false;
            res.totalCost -= sets[i].cost;
            for (int e : sets[i].elems) {
                if (e >= 0 && e < n) {
                    coverCount[e]--;
                }
            }
            changed = true;
        }
        return changed;
    }

    private static boolean dropAddPasses(SetDesc[] sets, int n, Result res, List<Integer>[] elemToSets) {
        int m = sets.length;
        boolean[] used = res.usedSets;
        boolean changed = false;

        List<Integer> chosen = new ArrayList<>();
        for (int i = 0; i < m; i++) {
            if (used[i]) {
                chosen.add(i);
            }
        }
        chosen.sort((a, b) -> Integer.compare(sets[b].cost, sets[a].cost));

        for (int drop : chosen) {
            if (!used[drop]) {
                continue;
            }
            boolean[] usedSnapshot = Arrays.copyOf(used, m);
            long costSnapshot = res.totalCost;
            int[] coverCount = buildCoverCount(sets, n, used);

            used[drop] = false;
            res.totalCost -= sets[drop].cost;
            for (int e : sets[drop].elems) {
                if (e >= 0 && e < n) {
                    coverCount[e]--;
                }
            }

            boolean[] uncovered = new boolean[n];
            int uncoveredCount = 0;
            for (int e = 0; e < n; e++) {
                if (coverCount[e] == 0) {
                    uncovered[e] = true;
                    uncoveredCount++;
                }
            }

            if (uncoveredCount == 0) {
                changed = true;
                continue;
            }

            if (!greedyRepair(sets, n, used, uncovered, uncoveredCount, elemToSets, res)
                    || res.totalCost >= costSnapshot) {
                System.arraycopy(usedSnapshot, 0, used, 0, m);
                res.totalCost = costSnapshot;
                continue;
            }
            changed = true;
        }
        return changed;
    }

    private static boolean greedyRepair(
            SetDesc[] sets,
            int n,
            boolean[] used,
            boolean[] uncovered,
            int uncoveredCount,
            List<Integer>[] elemToSets,
            Result res) {
        int m = sets.length;
        boolean[] candidate = new boolean[m];
        for (int e = 0; e < n; e++) {
            if (!uncovered[e]) {
                continue;
            }
            for (int si : elemToSets[e]) {
                candidate[si] = true;
            }
        }

        while (uncoveredCount > 0) {
            int bestSet = -1;
            double bestRatio = Double.POSITIVE_INFINITY;

            for (int i = 0; i < m; i++) {
                if (!candidate[i] || used[i] || sets[i] == null) {
                    continue;
                }
                int gain = 0;
                for (int e : sets[i].elems) {
                    if (e >= 0 && e < n && uncovered[e]) {
                        gain++;
                    }
                }
                if (gain == 0) {
                    continue;
                }
                double ratio = (double) sets[i].cost / gain;
                if (ratio < bestRatio) {
                    bestRatio = ratio;
                    bestSet = i;
                }
            }

            if (bestSet == -1) {
                return false;
            }

            used[bestSet] = true;
            res.totalCost += sets[bestSet].cost;
            for (int e : sets[bestSet].elems) {
                if (e >= 0 && e < n && uncovered[e]) {
                    uncovered[e] = false;
                    uncoveredCount--;
                }
            }
        }
        return true;
    }

    private static int[] buildCoverCount(SetDesc[] sets, int n, boolean[] used) {
        int[] coverCount = new int[n];
        for (int i = 0; i < sets.length; i++) {
            if (!used[i] || sets[i] == null) {
                continue;
            }
            for (int e : sets[i].elems) {
                if (e >= 0 && e < n) {
                    coverCount[e]++;
                }
            }
        }
        return coverCount;
    }
}