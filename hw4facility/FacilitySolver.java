import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class FacilitySolver {

    private static final double EPS = 1e-9;
    private static final double INF = 1e100;
    private static final int BAN_TRIES = 15;
    private static final int CONSIDERING_M = 10;
    private static final int PAIRWISE_OPT_ATTEMPTS = 3000;
    private static final int PAIRWISE_OPT_PASSES = 2;
    private static final long HUGE_TIME_LIMIT_NS = 10L * 60 * 1_000_000_000L;

    private static long hugeDeadlineNanos = Long.MAX_VALUE;

    public static long solveFromFile(String fileName) throws Exception {
        try (BufferedReader br = new BufferedReader(new FileReader(fileName))) {
            return solve(br);
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            System.out.println(solveFromFile(args[0]));
        }
    }

    public static long solve(BufferedReader br) throws IOException {
        Instance inst = Instance.read(br);
        if (!inst.feasible()) {
            return -1L;
        }
        double[][] dist = inst.distances();
        double v = solveHeuristic(inst, dist);
        return Math.round(v);
    }

    private static boolean isHuge(Instance inst) {
        return inst.n >= 1800 && inst.m >= 1800;
    }

    private static void beginHugeTimer() {
        hugeDeadlineNanos = System.nanoTime() + HUGE_TIME_LIMIT_NS;
    }

    private static void endHugeTimer() {
        hugeDeadlineNanos = Long.MAX_VALUE;
    }

    private static boolean hugeTimeExpired() {
        return hugeDeadlineNanos != Long.MAX_VALUE && System.nanoTime() >= hugeDeadlineNanos;
    }

    private static double solveHeuristic(Instance inst, double[][] dist) {
        if (isHuge(inst)) {
            return solveHuge(inst, dist);
        }
        int m = inst.m;
        int[] order = new int[m];
        Solution bestSol = null;
        double best = INF;

        Solution batch = solveBatchMultistart(inst, dist);
        if (batch != null) {
            batch = improveSolution(inst, dist, batch, false);
            double c = batch.totalCost(inst, dist);
            if (c < best - EPS) {
                best = c;
                bestSol = copySolution(batch);
            }
        }

        if (m <= 1200) {
            bestSol = considerOrder(inst, dist, orderByDemand(inst), bestSol, best);
            best = costOf(inst, dist, bestSol);
            bestSol = considerOrder(inst, dist, orderByRegret(inst, dist), bestSol, best);
            best = costOf(inst, dist, bestSol);

            Random rnd = new Random(0xFAC1117EL);
            for (int r = 0; r < randomStartCount(m); r++) {
                for (int i = 0; i < m; i++) {
                    order[i] = i;
                }
                shuffle(order, rnd);
                bestSol = considerOrder(inst, dist, order, bestSol, best);
                best = costOf(inst, dist, bestSol);
            }
        } else if (m <= 2000) {
            bestSol = considerOrderHeavy(inst, dist, orderByDemand(inst), bestSol, best);
            best = costOf(inst, dist, bestSol);
            bestSol = considerOrderHeavy(inst, dist, orderByRegret(inst, dist), bestSol, best);
            best = costOf(inst, dist, bestSol);

            Solution batchFull = solveBatchMultistart(inst, dist, inst.m);
            if (batchFull != null) {
                batchFull = improveSolution(inst, dist, batchFull, true);
                double c = batchFull.totalCost(inst, dist);
                if (c < best - EPS) {
                    best = c;
                    bestSol = copySolution(batchFull);
                }
            }
        } else if (bestSol == null) {
            bestSol = considerOrder(inst, dist, orderByDemand(inst), bestSol, best);
            best = costOf(inst, dist, bestSol);
        }

        return best < INF / 2 ? best : -1;
    }

    private static double solveHuge(Instance inst, double[][] dist) {
        beginHugeTimer();
        try {
            Solution bestSol = null;
            double best = INF;
            int scan = 300;

            if (!hugeTimeExpired()) {
                Solution batch = solveBatchMultistart(inst, dist, scan);
                if (batch != null) {
                    batch = improveSolutionHuge(inst, dist, batch);
                    double c = batch.totalCost(inst, dist);
                    if (c < best - EPS) {
                        best = c;
                        bestSol = batch;
                    }
                }
            }

            if (!hugeTimeExpired()) {
                Solution fallback = allOpenFallback(inst, dist, orderByDemand(inst));
                if (fallback != null) {
                    fallback = improveSolutionHuge(inst, dist, fallback);
                    double c = fallback.totalCost(inst, dist);
                    if (c < best - EPS) {
                        best = c;
                        bestSol = fallback;
                    }
                }
            }

            return best < INF / 2 ? best : -1;
        } finally {
            endHugeTimer();
        }
    }

    private static Solution improveSolutionHuge(Instance inst, double[][] dist, Solution sol) {
        if (sol == null) {
            return null;
        }
        Solution cur = copySolution(sol);
        Random rnd = new Random(0x48EE642L);
        int m = inst.m;
        for (int round = 0; round < 35 && !hugeTimeExpired(); round++) {
            boolean improved = false;
            if (relocatePass(inst, dist, cur)) {
                improved = true;
            }
            for (int t = 0; t < 1500 && !hugeTimeExpired(); t++) {
                int c1 = rnd.nextInt(m);
                int c2 = rnd.nextInt(m);
                if (c1 != c2 && trySwap(inst, dist, cur, c1, c2)) {
                    improved = true;
                    break;
                }
            }
            if (!improved) {
                break;
            }
        }
        closeEmptyFacilities(inst, cur);
        if (!hugeTimeExpired()) {
            pairwiseOpt(inst, dist, cur, false, 2000, 2);
        }
        if (!hugeTimeExpired()) {
            relocatePass(inst, dist, cur);
        }
        return cur;
    }

    private static Solution improveSolution(Instance inst, double[][] dist, Solution sol) {
        return improveSolution(inst, dist, sol, false);
    }

    private static Solution improveSolution(Instance inst, double[][] dist, Solution sol, boolean intensive) {
        sol = localSearch(inst, dist, sol, intensive);
        pairwiseOpt(inst, dist, sol, intensive);
        return localSearch(inst, dist, sol, intensive);
    }

    private static Solution lightImprove(Instance inst, double[][] dist, Solution sol) {
        return localSearch(inst, dist, sol, false);
    }

    private static Solution considerOrderHeavy(
            Instance inst, double[][] dist, int[] order, Solution bestSol, double bestCost) {
        Solution greedy = greedyConstruct(inst, dist, order);
        if (greedy != null) {
            Solution opt = improveSolution(inst, dist, greedy, true);
            double c = opt.totalCost(inst, dist);
            if (c < bestCost - EPS) {
                bestSol = copySolution(opt);
                bestCost = c;
            }
        }
        Solution fallback = allOpenFallback(inst, dist, order);
        if (fallback != null) {
            Solution opt = improveSolution(inst, dist, fallback, true);
            double c = opt.totalCost(inst, dist);
            if (c < bestCost - EPS) {
                bestSol = copySolution(opt);
            }
        }
        return bestSol;
    }

    private static Solution considerOrder(
            Instance inst, double[][] dist, int[] order, Solution bestSol, double bestCost) {
        Solution greedy = greedyConstruct(inst, dist, order);
        if (greedy != null) {
            Solution opt = lightImprove(inst, dist, greedy);
            double c = opt.totalCost(inst, dist);
            if (c < bestCost - EPS) {
                bestSol = copySolution(opt);
            }
        }
        Solution fallback = allOpenFallback(inst, dist, order);
        if (fallback != null) {
            Solution opt = lightImprove(inst, dist, fallback);
            double c = opt.totalCost(inst, dist);
            if (c < bestCost - EPS) {
                bestSol = copySolution(opt);
            }
        }
        return bestSol;
    }

    private static Solution solveBatchMultistart(Instance inst, double[][] dist) {
        return solveBatchMultistart(inst, dist, batchScanLimit(inst.m));
    }

    private static Solution solveBatchMultistart(Instance inst, double[][] dist, int scanLimit) {
        boolean[] banned = new boolean[inst.n];
        Solution best = batchGreedyConstruct(inst, dist, banned, scanLimit, isHuge(inst));
        if (best == null) {
            return null;
        }
        double[] ratings = computeShopRatings(inst, dist);
        int[] order = openShopsByRating(best, ratings);
        int tries = Math.min(isHuge(inst) ? 6 : banTries(inst.m), order.length);
        for (int i = 0; i < tries; i++) {
            if (hugeTimeExpired()) {
                break;
            }
            banned[order[i]] = true;
            Solution cand = batchGreedyConstruct(inst, dist, banned, scanLimit, isHuge(inst));
            if (cand != null && cand.totalCost(inst, dist) < best.totalCost(inst, dist) - EPS) {
                best = cand;
            }
        }
        return best;
    }

    private static double[] computeShopRatings(Instance inst, double[][] dist) {
        int n = inst.n;
        int m = inst.m;
        int k = Math.min(CONSIDERING_M, m);
        double[] ratings = new double[n];
        double[][] distToCustomers = dist;

        int[][] nearestByShop = new int[n][k];
        for (int f = 0; f < n; f++) {
            Integer[] ord = new Integer[m];
            for (int c = 0; c < m; c++) {
                ord[c] = c;
            }
            final int facility = f;
            Arrays.sort(ord, (a, b) -> Double.compare(distToCustomers[a][facility], distToCustomers[b][facility]));
            for (int j = 0; j < k; j++) {
                nearestByShop[f][j] = ord[j];
            }
        }

        double[] bestDistance = new double[m];
        Arrays.fill(bestDistance, INF);
        double norm = k;
        for (int c = 0; c < m; c++) {
            for (int f = 0; f < n; f++) {
                bestDistance[c] = Math.min(bestDistance[c], dist[c][f] + inst.openCost[f] / norm);
            }
        }

        for (int f = 0; f < n; f++) {
            int cnt = k;
            for (int j = 0; j < k; j++) {
                int c = nearestByShop[f][j];
                double curr = dist[c][f] + inst.openCost[f] / cnt;
                if (bestDistance[c] > EPS) {
                    ratings[f] += curr / bestDistance[c];
                }
            }
        }
        return ratings;
    }

    private static int[] openShopsByRating(Solution sol, double[] ratings) {
        List<Integer> shops = new ArrayList<>();
        for (int f = 0; f < sol.open.length; f++) {
            if (sol.open[f]) {
                shops.add(f);
            }
        }
        shops.sort((a, b) -> {
            int cmp = Double.compare(ratings[b], ratings[a]);
            return cmp != 0 ? cmp : Integer.compare(a, b);
        });
        int[] order = new int[shops.size()];
        for (int i = 0; i < shops.size(); i++) {
            order[i] = shops.get(i);
        }
        return order;
    }

    private static int banTries(int m) {
        if (m <= 500) {
            return BAN_TRIES;
        }
        if (m <= 2000) {
            return BAN_TRIES;
        }
        return 6;
    }

    private static int batchScanLimit(int m) {
        if (m <= 800) {
            return m;
        }
        if (m <= 1600) {
            return m;
        }
        return 350;
    }

    private static Solution batchGreedyConstruct(Instance inst, double[][] dist, boolean[] banned) {
        Solution sol = batchGreedyConstruct(inst, dist, banned, batchScanLimit(inst.m), false);
        if (sol == null && !isHuge(inst)) {
            sol = batchGreedyConstruct(inst, dist, banned, inst.m, false);
        }
        return sol;
    }

    private static Solution batchGreedyConstruct(
            Instance inst, double[][] dist, boolean[] banned, int scanLimit, boolean fastTopK) {
        int n = inst.n;
        int m = inst.m;
        boolean[] open = new boolean[n];
        double[] load = new double[n];
        int[] assign = new int[m];
        Arrays.fill(assign, -1);
        boolean[] chosen = new boolean[m];

        double[] distToClosestShop = new double[m];
        for (int c = 0; c < m; c++) {
            double best = INF;
            for (int f = 0; f < n; f++) {
                if (banned[f]) {
                    continue;
                }
                best = Math.min(best, dist[c][f] + inst.openCost[f]);
            }
            distToClosestShop[c] = best;
        }

        int assigned = 0;
        while (assigned < m) {
            if (fastTopK && hugeTimeExpired()) {
                return null;
            }
            int bestShop = -1;
            int[] bestCustomers = null;
            double bestCoef = INF;

            for (int f = 0; f < n; f++) {
                if (open[f] || banned[f]) {
                    continue;
                }
                final int facility = f;
                int scan = scanLimit;
                int[] nearest;
                if (fastTopK) {
                    nearest = topNearestUnchosen(dist, chosen, facility, scan, m);
                    scan = nearest.length;
                } else {
                    Integer[] ord = new Integer[m];
                    int cnt = 0;
                    for (int c = 0; c < m; c++) {
                        if (!chosen[c]) {
                            ord[cnt++] = c;
                        }
                    }
                    Arrays.sort(ord, 0, cnt, (a, b) -> {
                        double da = dist[a][facility];
                        double db = dist[b][facility];
                        return Double.compare(da * da, db * db);
                    });
                    scan = Math.min(cnt, scanLimit);
                    nearest = new int[scan];
                    for (int i = 0; i < scan; i++) {
                        nearest[i] = ord[i];
                    }
                }

                List<Integer> picked = new ArrayList<>();
                double capRem = inst.cap[f];
                double distSum = 0;
                for (int i = 0; i < scan; i++) {
                    int c = nearest[i];
                    double dem = inst.demand[c];
                    if (capRem + EPS < dem) {
                        continue;
                    }
                    if (dist[c][f] > distToClosestShop[c] + EPS) {
                        continue;
                    }
                    picked.add(c);
                    capRem -= dem;
                    distSum += dist[c][f];
                }
                if (picked.isEmpty()) {
                    continue;
                }
                double coef = (distSum + inst.openCost[f]) / picked.size();
                if (coef < bestCoef - EPS) {
                    bestCoef = coef;
                    bestShop = f;
                    bestCustomers = new int[picked.size()];
                    for (int pi = 0; pi < picked.size(); pi++) {
                        bestCustomers[pi] = picked.get(pi);
                    }
                }
            }

            if (bestShop < 0 || bestCustomers == null || bestCustomers.length == 0) {
                return null;
            }
            open[bestShop] = true;
            for (int c : bestCustomers) {
                chosen[c] = true;
                assign[c] = bestShop;
                load[bestShop] += inst.demand[c];
                assigned++;
            }
        }
        return new Solution(open, load, assign);
    }

    private static void pairwiseOpt(Instance inst, double[][] dist, Solution sol) {
        pairwiseOpt(inst, dist, sol, false);
    }

    private static int[] topNearestUnchosen(
            double[][] dist, boolean[] chosen, int facility, int k, int m) {
        int[] clients = new int[Math.min(k, m)];
        double[] bestD2 = new double[clients.length];
        int size = 0;
        for (int c = 0; c < m; c++) {
            if (chosen[c]) {
                continue;
            }
            double d = dist[c][facility];
            double d2 = d * d;
            if (size < clients.length) {
                clients[size] = c;
                bestD2[size] = d2;
                size++;
            } else if (d2 < bestD2[size - 1]) {
                clients[size - 1] = c;
                bestD2[size - 1] = d2;
            } else {
                continue;
            }
            for (int i = size - 1; i > 0 && bestD2[i] < bestD2[i - 1]; i--) {
                double td = bestD2[i];
                bestD2[i] = bestD2[i - 1];
                bestD2[i - 1] = td;
                int tc = clients[i];
                clients[i] = clients[i - 1];
                clients[i - 1] = tc;
            }
        }
        return Arrays.copyOf(clients, size);
    }

    private static void pairwiseOpt(Instance inst, double[][] dist, Solution sol, boolean intensive) {
        int attempts = intensive ? PAIRWISE_OPT_ATTEMPTS * 2 : PAIRWISE_OPT_ATTEMPTS;
        int passes = intensive ? PAIRWISE_OPT_PASSES + 1 : PAIRWISE_OPT_PASSES;
        pairwiseOpt(inst, dist, sol, intensive, attempts, passes);
    }

    private static void pairwiseOpt(
            Instance inst, double[][] dist, Solution sol, boolean intensive, int attempts, int passes) {
        int m = inst.m;
        if (m < 2) {
            return;
        }
        Random rnd = new Random(0x2F0F1A42L);
        int randomAttempts = attempts / 5;
        int greedyAttempts = attempts - randomAttempts;
        for (int pass = 0; pass < passes && !hugeTimeExpired(); pass++) {
            for (int t = 0; t < randomAttempts && !hugeTimeExpired(); t++) {
                int c1 = rnd.nextInt(m);
                int c2 = rnd.nextInt(m);
                if (c1 != c2) {
                    trySwap(inst, dist, sol, c1, c2);
                }
            }
            for (int t = 0; t < greedyAttempts && !hugeTimeExpired(); t++) {
                boolean improved = false;
                int c1 = rnd.nextInt(m);
                for (int k = 0; k < m && !improved && !hugeTimeExpired(); k++) {
                    int c2 = (c1 + 1 + k) % m;
                    if (c1 != c2 && trySwap(inst, dist, sol, c1, c2)) {
                        improved = true;
                    }
                }
            }
        }
    }

    private static double costOf(Instance inst, double[][] dist, Solution sol) {
        return sol == null ? INF : sol.totalCost(inst, dist);
    }

    private static Solution copySolution(Solution sol) {
        return new Solution(
                Arrays.copyOf(sol.open, sol.open.length),
                Arrays.copyOf(sol.load, sol.load.length),
                Arrays.copyOf(sol.assign, sol.assign.length));
    }

    private static int maxLsRounds(int m) {
        return maxLsRounds(m, false);
    }

    private static int maxLsRounds(int m, boolean intensive) {
        if (intensive) {
            if (m <= 200) {
                return 250;
            }
            if (m <= 1600) {
                return 180;
            }
            return 80;
        }
        if (m <= 200) {
            return 200;
        }
        if (m <= 1000) {
            return 120;
        }
        return 60;
    }

    private static Solution localSearch(Instance inst, double[][] dist, Solution sol) {
        return localSearch(inst, dist, sol, false);
    }

    private static Solution localSearch(Instance inst, double[][] dist, Solution sol, boolean intensive) {
        if (sol == null) {
            return null;
        }
        Solution cur = copySolution(sol);
        boolean improved = true;
        int rounds = 0;
        int maxRounds = maxLsRounds(inst.m, intensive);
        while (improved && rounds++ < maxRounds) {
            improved = false;
            if (relocatePass(inst, dist, cur)) {
                improved = true;
            }
            if (swapPass(inst, dist, cur)) {
                improved = true;
            }
            if (triplePass(inst, dist, cur)) {
                improved = true;
            }
            if (facilitySwapPass(inst, dist, cur)) {
                improved = true;
            }
        }
        closeEmptyFacilities(inst, cur);
        return cur;
    }

    private static void closeEmptyFacilities(Instance inst, Solution sol) {
        for (int f = 0; f < inst.n; f++) {
            if (sol.open[f] && sol.load[f] <= EPS) {
                sol.open[f] = false;
            }
        }
    }

    private static boolean relocatePass(Instance inst, double[][] dist, Solution sol) {
        int m = inst.m;
        int n = inst.n;
        for (int c = 0; c < m; c++) {
            int from = sol.assign[c];
            double dem = inst.demand[c];
            for (int f = 0; f < n; f++) {
                if (f == from) {
                    continue;
                }
                if (inst.cap[f] + EPS < dem) {
                    continue;
                }
                if (sol.open[f] && sol.load[f] + dem > inst.cap[f] + EPS) {
                    continue;
                }
                double delta = dist[c][f] - dist[c][from];
                if (!sol.open[f]) {
                    delta += inst.openCost[f];
                }
                if (Math.abs(sol.load[from] - dem) <= EPS) {
                    delta -= inst.openCost[from];
                }
                if (delta < -EPS) {
                    applyRelocate(inst, sol, c, from, f, dem);
                    return true;
                }
            }
        }
        return false;
    }

    private static void applyRelocate(Instance inst, Solution sol, int c, int from, int to, double dem) {
        sol.load[from] -= dem;
        if (sol.load[from] <= EPS) {
            sol.open[from] = false;
            sol.load[from] = 0;
        }
        if (!sol.open[to]) {
            sol.open[to] = true;
            sol.load[to] = 0;
        }
        sol.load[to] += dem;
        sol.assign[c] = to;
    }

    private static boolean swapPass(Instance inst, double[][] dist, Solution sol) {
        int m = inst.m;
        if (m > 600) {
            Random rnd = new Random(0x5BA9200L);
            for (int t = 0; t < 8000; t++) {
                int c1 = rnd.nextInt(m);
                int c2 = rnd.nextInt(m);
                if (c1 == c2) {
                    continue;
                }
                if (c2 < c1) {
                    int x = c1;
                    c1 = c2;
                    c2 = x;
                }
                if (trySwap(inst, dist, sol, c1, c2)) {
                    return true;
                }
            }
            return false;
        }
        for (int c1 = 0; c1 < m; c1++) {
            for (int c2 = c1 + 1; c2 < m; c2++) {
                if (trySwap(inst, dist, sol, c1, c2)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean trySwap(Instance inst, double[][] dist, Solution sol, int c1, int c2) {
        int f1 = sol.assign[c1];
        int f2 = sol.assign[c2];
        if (f1 == f2) {
            return false;
        }
        double d1 = inst.demand[c1];
        double d2 = inst.demand[c2];
        if (sol.load[f1] - d1 + d2 > inst.cap[f1] + EPS) {
            return false;
        }
        if (sol.load[f2] - d2 + d1 > inst.cap[f2] + EPS) {
            return false;
        }
        double delta = dist[c1][f2] + dist[c2][f1] - dist[c1][f1] - dist[c2][f2];
        if (delta < -EPS) {
            sol.load[f1] += d2 - d1;
            sol.load[f2] += d1 - d2;
            sol.assign[c1] = f2;
            sol.assign[c2] = f1;
            return true;
        }
        return false;
    }

    private static boolean triplePass(Instance inst, double[][] dist, Solution sol) {
        int m = inst.m;
        if (m > 200) {
            Random rnd = new Random(0x7F1D13EL);
            for (int t = 0; t < 4000; t++) {
                int c1 = rnd.nextInt(m);
                int c2 = rnd.nextInt(m);
                int c3 = rnd.nextInt(m);
                if (c1 == c2 || c1 == c3 || c2 == c3) {
                    continue;
                }
                if (c2 < c1) {
                    int x = c1;
                    c1 = c2;
                    c2 = x;
                }
                if (c3 < c2) {
                    int x = c2;
                    c2 = c3;
                    c3 = x;
                }
                if (c2 < c1) {
                    continue;
                }
                if (tryTriplePermutation(
                        inst,
                        dist,
                        sol,
                        c1,
                        c2,
                        c3,
                        sol.assign[c1],
                        sol.assign[c2],
                        sol.assign[c3],
                        inst.demand[c1],
                        inst.demand[c2],
                        inst.demand[c3])) {
                    return true;
                }
            }
            return false;
        }
        for (int c1 = 0; c1 < m; c1++) {
            int f1 = sol.assign[c1];
            double d1 = inst.demand[c1];
            for (int c2 = c1 + 1; c2 < m; c2++) {
                int f2 = sol.assign[c2];
                double d2 = inst.demand[c2];
                for (int c3 = c2 + 1; c3 < m; c3++) {
                    int f3 = sol.assign[c3];
                    double d3 = inst.demand[c3];
                    if (tryTriplePermutation(inst, dist, sol, c1, c2, c3, f1, f2, f3, d1, d2, d3)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean tryTriplePermutation(
            Instance inst,
            double[][] dist,
            Solution sol,
            int c1,
            int c2,
            int c3,
            int f1,
            int f2,
            int f3,
            double d1,
            double d2,
            double d3) {
        int[] clients = {c1, c2, c3};
        int[] facs = {f1, f2, f3};
        double[] dems = {d1, d2, d3};
        int[] curAssign = {f1, f2, f3};
        double base = dist[c1][f1] + dist[c2][f2] + dist[c3][f3];

        int[] perm = {0, 1, 2};
        do {
            double[] loadDelta = new double[inst.n];
            boolean ok = true;
            for (int i = 0; i < 3; i++) {
                int c = clients[i];
                int oldF = curAssign[i];
                int newF = facs[perm[i]];
                loadDelta[oldF] -= dems[i];
                loadDelta[newF] += dems[i];
            }
            for (int i = 0; i < 3; i++) {
                int newF = facs[perm[i]];
                if (!sol.open[newF] && loadDelta[newF] > EPS && inst.cap[newF] + EPS < dems[i]) {
                    ok = false;
                    break;
                }
            }
            if (!ok) {
                continue;
            }
            for (int f = 0; f < inst.n; f++) {
                if (Math.abs(loadDelta[f]) <= EPS) {
                    continue;
                }
                if (sol.load[f] + loadDelta[f] > inst.cap[f] + EPS) {
                    ok = false;
                    break;
                }
                if (loadDelta[f] > EPS && !sol.open[f] && inst.cap[f] + EPS < loadDelta[f]) {
                    ok = false;
                    break;
                }
            }
            if (!ok) {
                continue;
            }

            double newDist = 0;
            for (int i = 0; i < 3; i++) {
                newDist += dist[clients[i]][facs[perm[i]]];
            }
            double delta = newDist - base;
            for (int f = 0; f < inst.n; f++) {
                if (loadDelta[f] < -EPS && Math.abs(sol.load[f] + loadDelta[f]) <= EPS && sol.open[f]) {
                    delta -= inst.openCost[f];
                }
                if (loadDelta[f] > EPS && !sol.open[f]) {
                    delta += inst.openCost[f];
                }
            }
            if (delta < -EPS) {
                for (int i = 0; i < 3; i++) {
                    int c = clients[i];
                    int oldF = curAssign[i];
                    int newF = facs[perm[i]];
                    sol.load[oldF] -= dems[i];
                    if (sol.load[oldF] <= EPS) {
                        sol.open[oldF] = false;
                        sol.load[oldF] = 0;
                    }
                    if (!sol.open[newF]) {
                        sol.open[newF] = true;
                        sol.load[newF] = 0;
                    }
                    sol.load[newF] += dems[i];
                    sol.assign[c] = newF;
                }
                return true;
            }
        } while (nextPermutation(perm));
        return false;
    }

    private static boolean nextPermutation(int[] a) {
        int i = a.length - 1;
        while (i > 0 && a[i - 1] >= a[i]) {
            i--;
        }
        if (i == 0) {
            return false;
        }
        int j = a.length - 1;
        while (a[j] <= a[i - 1]) {
            j--;
        }
        int t = a[i - 1];
        a[i - 1] = a[j];
        a[j] = t;
        for (int l = i, r = a.length - 1; l < r; l++, r--) {
            t = a[l];
            a[l] = a[r];
            a[r] = t;
        }
        return true;
    }

    private static boolean facilitySwapPass(Instance inst, double[][] dist, Solution sol) {
        int n = inst.n;
        int m = inst.m;
        if (n > 300 || m > 1500) {
            return false;
        }
        for (int fClose = 0; fClose < n; fClose++) {
            if (!sol.open[fClose] || sol.load[fClose] <= EPS) {
                continue;
            }
            int[] clients = new int[m];
            int cnt = 0;
            for (int c = 0; c < m; c++) {
                if (sol.assign[c] == fClose) {
                    clients[cnt++] = c;
                }
            }
            if (cnt == 0 || cnt > 25) {
                continue;
            }
            for (int fOpen = 0; fOpen < n; fOpen++) {
                if (sol.open[fOpen] || fOpen == fClose) {
                    continue;
                }
                double deltaOpen = inst.openCost[fOpen] - inst.openCost[fClose];
                double[] loads = Arrays.copyOf(sol.load, n);
                boolean[] open = Arrays.copyOf(sol.open, n);
                open[fClose] = false;
                open[fOpen] = true;
                loads[fClose] = 0;
                loads[fOpen] = 0;
                int[] assign = Arrays.copyOf(sol.assign, m);
                double deltaDist = 0;
                boolean ok = true;
                for (int i = 0; i < cnt; i++) {
                    int c = clients[i];
                    double dem = inst.demand[c];
                    deltaDist -= dist[c][fClose];
                    int best = -1;
                    double bestD = INF;
                    for (int f = 0; f < n; f++) {
                        if (!open[f] && f != fOpen) {
                            continue;
                        }
                        if (f == fClose) {
                            continue;
                        }
                        if (!open[f] && inst.cap[f] + EPS < dem) {
                            continue;
                        }
                        if (open[f] && loads[f] + dem > inst.cap[f] + EPS) {
                            continue;
                        }
                        if (dist[c][f] < bestD - EPS) {
                            bestD = dist[c][f];
                            best = f;
                        }
                    }
                    if (best < 0) {
                        ok = false;
                        break;
                    }
                    if (!open[best]) {
                        deltaOpen += inst.openCost[best];
                        open[best] = true;
                        loads[best] = 0;
                    }
                    deltaDist += bestD;
                    loads[best] += dem;
                    assign[c] = best;
                }
                if (!ok) {
                    continue;
                }
                double delta = deltaOpen + deltaDist;
                if (delta < -EPS) {
                    sol.open = open;
                    sol.load = loads;
                    sol.assign = assign;
                    closeEmptyFacilities(inst, sol);
                    return true;
                }
            }
        }
        return false;
    }

    private static int randomStartCount(int m) {
        if (m <= 50) {
            return 12;
        }
        if (m <= 200) {
            return 8;
        }
        if (m <= 500) {
            return 6;
        }
        if (m <= 1500) {
            return 4;
        }
        return 3;
    }

    private static int[] orderByDemand(Instance inst) {
        int m = inst.m;
        Integer[] ord = new Integer[m];
        for (int i = 0; i < m; i++) {
            ord[i] = i;
        }
        Arrays.sort(ord, (a, b) -> Double.compare(inst.demand[b], inst.demand[a]));
        int[] order = new int[m];
        for (int i = 0; i < m; i++) {
            order[i] = ord[i];
        }
        return order;
    }

    private static int[] orderByMinDist(Instance inst, double[][] dist, boolean farFirst) {
        int m = inst.m;
        double[] nearest = new double[m];
        for (int c = 0; c < m; c++) {
            double best = INF;
            for (int f = 0; f < inst.n; f++) {
                best = Math.min(best, dist[c][f]);
            }
            nearest[c] = best;
        }
        Integer[] ord = new Integer[m];
        for (int i = 0; i < m; i++) {
            ord[i] = i;
        }
        if (farFirst) {
            Arrays.sort(ord, (a, b) -> Double.compare(nearest[b], nearest[a]));
        } else {
            Arrays.sort(ord, (a, b) -> Double.compare(nearest[a], nearest[b]));
        }
        int[] order = new int[m];
        for (int i = 0; i < m; i++) {
            order[i] = ord[i];
        }
        return order;
    }

    private static int[] orderByRegret(Instance inst, double[][] dist) {
        int m = inst.m;
        double[] regret = new double[m];
        for (int c = 0; c < m; c++) {
            double best = INF;
            double second = INF;
            for (int f = 0; f < inst.n; f++) {
                double d = dist[c][f];
                if (d < best - EPS) {
                    second = best;
                    best = d;
                } else if (d < second - EPS) {
                    second = d;
                }
            }
            regret[c] = second < INF / 2 ? second - best : 0;
        }
        Integer[] ord = new Integer[m];
        for (int i = 0; i < m; i++) {
            ord[i] = i;
        }
        Arrays.sort(ord, (a, b) -> Double.compare(regret[b], regret[a]));
        int[] order = new int[m];
        for (int i = 0; i < m; i++) {
            order[i] = ord[i];
        }
        return order;
    }

    private static void shuffle(int[] order, Random rnd) {
        for (int i = order.length - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            int t = order[i];
            order[i] = order[j];
            order[j] = t;
        }
    }

    private static Solution allOpenFallback(Instance inst, double[][] dist, int[] order) {
        int n = inst.n;
        int m = inst.m;
        boolean[] open = new boolean[n];
        Arrays.fill(open, true);
        double[] load = new double[n];
        int[] assign = new int[m];
        Arrays.fill(assign, -1);
        for (int idx = 0; idx < m; idx++) {
            int c = order[idx];
            int best = -1;
            double bd = INF;
            for (int f = 0; f < n; f++) {
                if (load[f] + inst.demand[c] <= inst.cap[f] + EPS && dist[c][f] < bd - EPS) {
                    bd = dist[c][f];
                    best = f;
                }
            }
            if (best < 0) {
                return null;
            }
            assign[c] = best;
            load[best] += inst.demand[c];
        }
        for (int f = 0; f < n; f++) {
            if (load[f] <= EPS) {
                open[f] = false;
            }
        }
        return new Solution(open, load, assign);
    }

    private static Solution greedyConstruct(Instance inst, double[][] dist, int[] order) {
        int n = inst.n;
        int m = inst.m;
        boolean[] open = new boolean[n];
        double[] load = new double[n];
        int[] assign = new int[m];
        Arrays.fill(assign, -1);

        for (int idx = 0; idx < m; idx++) {
            int c = order[idx];
            double dem = inst.demand[c];
            int bestOpen = -1;
            double bestD = INF;
            for (int f = 0; f < n; f++) {
                if (!open[f]) {
                    continue;
                }
                if (load[f] + dem <= inst.cap[f] + EPS && dist[c][f] < bestD - EPS) {
                    bestD = dist[c][f];
                    bestOpen = f;
                }
            }
            if (bestOpen >= 0) {
                assign[c] = bestOpen;
                load[bestOpen] += dem;
                continue;
            }
            int bestClosed = -1;
            double bestScore = INF;
            for (int f = 0; f < n; f++) {
                if (open[f]) {
                    continue;
                }
                if (inst.cap[f] + EPS < dem) {
                    continue;
                }
                double score = inst.openCost[f] + dist[c][f];
                if (score < bestScore - EPS) {
                    bestScore = score;
                    bestClosed = f;
                }
            }
            if (bestClosed < 0) {
                return null;
            }
            open[bestClosed] = true;
            assign[c] = bestClosed;
            load[bestClosed] += dem;
        }
        return new Solution(open, load, assign);
    }

    private static final class Solution {
        boolean[] open;
        double[] load;
        int[] assign;

        Solution(boolean[] open, double[] load, int[] assign) {
            this.open = open;
            this.load = load;
            this.assign = assign;
        }

        double totalCost(Instance inst, double[][] dist) {
            double t = 0;
            for (int f = 0; f < inst.n; f++) {
                if (open[f]) {
                    t += inst.openCost[f];
                }
            }
            for (int c = 0; c < inst.m; c++) {
                t += dist[c][assign[c]];
            }
            return t;
        }
    }

    private static final class Instance {
        final int n;
        final int m;
        final double[] openCost;
        final double[] cap;
        final double[] fx;
        final double[] fy;
        final double[] demand;
        final double[] cx;
        final double[] cy;
        final double totalDemand;

        Instance(
                int n,
                int m,
                double[] openCost,
                double[] cap,
                double[] fx,
                double[] fy,
                double[] demand,
                double[] cx,
                double[] cy) {
            this.n = n;
            this.m = m;
            this.openCost = openCost;
            this.cap = cap;
            this.fx = fx;
            this.fy = fy;
            this.demand = demand;
            this.cx = cx;
            this.cy = cy;
            double s = 0;
            for (double d : demand) {
                s += d;
            }
            this.totalDemand = s;
        }

        static Instance read(BufferedReader br) throws IOException {
            String line;
            do {
                line = br.readLine();
                if (line == null) {
                    throw new IOException("empty input");
                }
                line = line.trim();
            } while (line.isEmpty());

            String[] head = line.split("\\s+");
            int n = Integer.parseInt(head[0]);
            int m = Integer.parseInt(head[1]);
            double[] openCost = new double[n];
            double[] cap = new double[n];
            double[] fx = new double[n];
            double[] fy = new double[n];
            for (int i = 0; i < n; i++) {
                line = nextNonEmpty(br);
                String[] p = line.split("\\s+");
                openCost[i] = Double.parseDouble(p[0]);
                cap[i] = Double.parseDouble(p[1]);
                fx[i] = Double.parseDouble(p[2]);
                fy[i] = Double.parseDouble(p[3]);
            }
            double[] demand = new double[m];
            double[] cx = new double[m];
            double[] cy = new double[m];
            for (int i = 0; i < m; i++) {
                line = nextNonEmpty(br);
                String[] p = line.split("\\s+");
                demand[i] = Double.parseDouble(p[0]);
                cx[i] = Double.parseDouble(p[1]);
                cy[i] = Double.parseDouble(p[2]);
            }
            return new Instance(n, m, openCost, cap, fx, fy, demand, cx, cy);
        }

        static String nextNonEmpty(BufferedReader br) throws IOException {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    return line;
                }
            }
            throw new IOException("unexpected EOF");
        }

        boolean feasible() {
            double capSum = 0;
            double maxCap = 0;
            for (int j = 0; j < n; j++) {
                capSum += cap[j];
                maxCap = Math.max(maxCap, cap[j]);
            }
            double maxDem = 0;
            for (int i = 0; i < m; i++) {
                maxDem = Math.max(maxDem, demand[i]);
            }
            return capSum + EPS >= totalDemand && maxCap + EPS >= maxDem;
        }

        double[][] distances() {
            double[][] d = new double[m][n];
            for (int i = 0; i < m; i++) {
                for (int j = 0; j < n; j++) {
                    double dx = cx[i] - fx[j];
                    double dy = cy[i] - fy[j];
                    d[i][j] = Math.hypot(dx, dy);
                }
            }
            return d;
        }
    }
}
