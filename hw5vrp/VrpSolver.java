import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class VrpSolver {

    private static final double INF = 1e100;
    private static final long EXACT_ASSIGN_LIMIT = 60_000_000L;
    private static final int RELOCATE_MAX_PASSES = 250;
    private static final int MULTISTART_CAP = 40;

    public static long solveFromFile(String fileName) throws Exception {
        try (BufferedReader br = new BufferedReader(new FileReader(fileName))) {
            return solve(br);
        }
    }

    public static long solve(BufferedReader br) throws IOException {
        Instance inst = Instance.read(br);
        if (!inst.feasible()) {
            return -1L;
        }
        double[][] dist = inst.distances();
        int n = inst.n;
        int V = inst.vehicles;
        int c = inst.cap;
        int K = n - 1;

        if (K <= 0) {
            return 0L;
        }

        long assignCount = assignmentCount(V, K);
        double cost;
        if (assignCount <= EXACT_ASSIGN_LIMIT) {
            cost = solveExact(inst.demand, dist, n, V, c, K);
        } else {
            cost = solveGreedyLarge(inst.demand, dist, n, V, c);
        }

        if (cost >= INF / 2 || Double.isNaN(cost)) {
            return -1L;
        }
        return Math.round(cost);
    }

    private static long assignmentCount(int V, int K) {
        long p = 1;
        for (int i = 0; i < K; i++) {
            if (p > EXACT_ASSIGN_LIMIT / V) {
                return Long.MAX_VALUE;
            }
            p *= V;
        }
        return p;
    }

    private static double solveExact(int[] demand, double[][] dist, int n, int V, int c, int K) {
        double[] tsp = precomputeTsp(dist, K);
        long total = assignmentCount(V, K);
        double best = INF;
        int[] assign = new int[K];

        for (long code = 0; code < total; code++) {
            long t = code;
            for (int i = 0; i < K; i++) {
                assign[i] = (int) (t % V);
                t /= V;
            }
            int[] load = new int[V];
            boolean ok = true;
            for (int i = 0; i < K; i++) {
                load[assign[i]] += demand[i + 1];
                if (load[assign[i]] > c) {
                    ok = false;
                }
            }
            if (!ok) {
                continue;
            }
            int[] masks = new int[V];
            for (int i = 0; i < K; i++) {
                masks[assign[i]] |= (1 << i);
            }
            double sum = 0;
            for (int v = 0; v < V; v++) {
                sum += tsp[masks[v]];
            }
            if (sum < best) {
                best = sum;
            }
        }
        return best;
    }

    private static double[] precomputeTsp(double[][] dist, int K) {
        int maxM = 1 << K;
        double[] tsp = new double[maxM];
        tsp[0] = 0;
        if (K == 0) {
            return tsp;
        }
        double[][] dp = new double[maxM][K];

        for (int mask = 1; mask < maxM; mask++) {
            for (int j = 0; j < K; j++) {
                if ((mask & (1 << j)) == 0) {
                    continue;
                }
                int bj = 1 << j;
                if (mask == bj) {
                    dp[mask][j] = dist[0][j + 1];
                } else {
                    double local = INF;
                    for (int i = 0; i < K; i++) {
                        if (i == j || (mask & (1 << i)) == 0) {
                            continue;
                        }
                        local = Math.min(local, dp[mask ^ bj][i] + dist[i + 1][j + 1]);
                    }
                    dp[mask][j] = local;
                }
            }
            double route = INF;
            for (int j = 0; j < K; j++) {
                if ((mask & (1 << j)) != 0) {
                    route = Math.min(route, dp[mask][j] + dist[j + 1][0]);
                }
            }
            tsp[mask] = route;
        }
        return tsp;
    }

    private static double solveGreedyLarge(int[] demand, double[][] dist, int n, int V, int c) {
        Plan best = null;
        Plan[] plans = {
            buildGreedySequential(demand, dist, n, V, c, true),
            buildGreedySequential(demand, dist, n, V, c, false),
            buildGreedyFfd(demand, dist, n, V, c, true),
            buildGreedyFfd(demand, dist, n, V, c, false),
        };
        for (Plan p : plans) {
            if (p != null && p.cost < INF / 2 && (best == null || p.cost < best.cost)) {
                best = p;
            }
        }
        if (best == null) {
            return INF;
        }
        return relocateImprove(dist, best.trucks, demand, V, c);
    }

    @SuppressWarnings("unchecked")
    private static Plan buildGreedySequential(int[] demand, double[][] dist, int n, int V, int c,
            boolean nearestFromLast) {
        boolean[] assigned = new boolean[n];
        assigned[0] = true;
        int remaining = n - 1;
        List<Integer>[] trucks = new List[V];
        for (int v = 0; v < V; v++) {
            trucks[v] = new ArrayList<>();
        }
        int routes = 0;

        while (remaining > 0) {
            if (routes >= V) {
                return null;
            }
            List<Integer> route = new ArrayList<>();
            int load = 0;
            boolean progress;
            do {
                progress = false;
                int best = -1;
                int bestPos = 0;
                double bestCost = INF;
                for (int j = 1; j < n; j++) {
                    if (assigned[j] || load + demand[j] > c) {
                        continue;
                    }
                    if (route.isEmpty()) {
                        double cost = dist[0][j];
                        if (cost < bestCost) {
                            bestCost = cost;
                            best = j;
                            bestPos = 0;
                        }
                        continue;
                    }
                    if (nearestFromLast) {
                        int last = route.get(route.size() - 1);
                        double cost = dist[last][j];
                        if (cost < bestCost) {
                            bestCost = cost;
                            best = j;
                            bestPos = route.size();
                        }
                    } else {
                        for (int pos = 0; pos <= route.size(); pos++) {
                            double cost = insertionDelta(dist, route, pos, j);
                            if (cost < bestCost) {
                                bestCost = cost;
                                best = j;
                                bestPos = pos;
                            }
                        }
                    }
                }
                if (best < 0) {
                    break;
                }
                route.add(bestPos, best);
                load += demand[best];
                assigned[best] = true;
                remaining--;
                progress = true;
            } while (progress);

            if (route.isEmpty() && remaining > 0) {
                return null;
            }
            trucks[routes].addAll(route);
            routes++;
        }
        double cost = totalTourCost(dist, trucks);
        return cost >= INF / 2 ? null : new Plan(trucks, cost);
    }

    private static double insertionDelta(double[][] dist, List<Integer> route, int pos, int j) {
        if (route.isEmpty()) {
            return dist[0][j] + dist[j][0];
        }
        if (pos == 0) {
            int a = route.get(0);
            return dist[0][j] + dist[j][a] - dist[0][a];
        }
        if (pos == route.size()) {
            int a = route.get(route.size() - 1);
            return dist[a][j] + dist[j][0] - dist[a][0];
        }
        int a = route.get(pos - 1);
        int b = route.get(pos);
        return dist[a][j] + dist[j][b] - dist[a][b];
    }

    @SuppressWarnings("unchecked")
    private static Plan buildGreedyFfd(int[] demand, double[][] dist, int n, int V, int c,
            boolean bestFit) {
        Integer[] order = new Integer[n - 1];
        for (int i = 0; i < order.length; i++) {
            order[i] = i + 1;
        }
        Arrays.sort(order, Comparator.comparingInt(j -> -demand[j]));

        int[] load = new int[V];
        int[] truck = new int[n];
        Arrays.fill(truck, -1);

        for (int j : order) {
            int placed = -1;
            if (bestFit) {
                int bestSlack = Integer.MAX_VALUE;
                for (int v = 0; v < V; v++) {
                    if (load[v] + demand[j] <= c) {
                        int slack = c - load[v] - demand[j];
                        if (slack < bestSlack) {
                            bestSlack = slack;
                            placed = v;
                        }
                    }
                }
            } else {
                for (int v = 0; v < V; v++) {
                    if (load[v] + demand[j] <= c) {
                        placed = v;
                        break;
                    }
                }
            }
            if (placed < 0) {
                return null;
            }
            load[placed] += demand[j];
            truck[j] = placed;
        }

        List<Integer>[] byTruck = new List[V];
        for (int v = 0; v < V; v++) {
            byTruck[v] = new ArrayList<>();
        }
        for (int j = 1; j < n; j++) {
            byTruck[truck[j]].add(j);
        }

        double total = totalTourCost(dist, byTruck);
        return total >= INF / 2 ? null : new Plan(byTruck, total);
    }

    private static double totalTourCost(double[][] dist, List<Integer>[] trucks) {
        double total = 0;
        for (List<Integer> route : trucks) {
            total += routeCost(dist, route);
        }
        return total;
    }

    private static double routeCost(double[][] dist, List<Integer> route) {
        return nnTourFromDepot(dist, route);
    }

    private static double[] truckCosts(double[][] dist, List<Integer>[] trucks) {
        double[] c = new double[trucks.length];
        for (int v = 0; v < trucks.length; v++) {
            c[v] = routeCost(dist, trucks[v]);
        }
        return c;
    }

    private static double sumCosts(double[] costs) {
        double s = 0;
        for (double x : costs) {
            s += x;
        }
        return s;
    }

    private static int routeLoad(int[] demand, List<Integer> route) {
        int load = 0;
        for (int j : route) {
            load += demand[j];
        }
        return load;
    }

    @SuppressWarnings("unchecked")
    private static double relocateImprove(double[][] dist, List<Integer>[] trucks, int[] demand, int V,
            int cap) {
        boolean trySwap = V <= 20;
        double[] tCost = truckCosts(dist, trucks);
        double cost = sumCosts(tCost);
        for (int pass = 0; pass < RELOCATE_MAX_PASSES; pass++) {
            double bestNext = cost;
            int bestFrom = -1;
            int bestTo = -1;
            int bestI = -1;
            int bestK = -1;
            boolean bestSwap = false;

            for (int from = 0; from < V; from++) {
                for (int i = 0; i < trucks[from].size(); i++) {
                    int j = trucks[from].get(i);
                    for (int to = 0; to < V; to++) {
                        if (to == from) {
                            continue;
                        }
                        if (routeLoad(demand, trucks[to]) + demand[j] > cap) {
                            continue;
                        }
                        trucks[from].remove(i);
                        trucks[to].add(j);
                        double next = cost - tCost[from] - tCost[to]
                                + routeCost(dist, trucks[from]) + routeCost(dist, trucks[to]);
                        if (next < bestNext - 1e-9) {
                            bestNext = next;
                            bestFrom = from;
                            bestTo = to;
                            bestI = i;
                            bestK = -1;
                            bestSwap = false;
                        }
                        trucks[to].remove(trucks[to].size() - 1);
                        trucks[from].add(i, j);
                    }
                }
                if (trySwap) {
                    for (int to = from + 1; to < V; to++) {
                        for (int i = 0; i < trucks[from].size(); i++) {
                            int j = trucks[from].get(i);
                            for (int k = 0; k < trucks[to].size(); k++) {
                                int w = trucks[to].get(k);
                                int loadFrom = routeLoad(demand, trucks[from]) - demand[j] + demand[w];
                                int loadTo = routeLoad(demand, trucks[to]) - demand[w] + demand[j];
                                if (loadFrom > cap || loadTo > cap) {
                                    continue;
                                }
                                trucks[from].set(i, w);
                                trucks[to].set(k, j);
                                double next = cost - tCost[from] - tCost[to]
                                        + routeCost(dist, trucks[from]) + routeCost(dist, trucks[to]);
                                if (next < bestNext - 1e-9) {
                                    bestNext = next;
                                    bestFrom = from;
                                    bestTo = to;
                                    bestI = i;
                                    bestK = k;
                                    bestSwap = true;
                                }
                                trucks[from].set(i, j);
                                trucks[to].set(k, w);
                            }
                        }
                    }
                }
            }

            if (bestFrom < 0 || bestNext >= cost - 1e-9) {
                break;
            }
            if (bestSwap) {
                int j = trucks[bestFrom].get(bestI);
                int w = trucks[bestTo].get(bestK);
                trucks[bestFrom].set(bestI, w);
                trucks[bestTo].set(bestK, j);
            } else {
                int j = trucks[bestFrom].remove(bestI);
                trucks[bestTo].add(j);
            }
            tCost[bestFrom] = routeCost(dist, trucks[bestFrom]);
            tCost[bestTo] = routeCost(dist, trucks[bestTo]);
            cost = sumCosts(tCost);
        }
        return cost;
    }

    private static final class Plan {
        final List<Integer>[] trucks;
        final double cost;

        Plan(List<Integer>[] trucks, double cost) {
            this.trucks = trucks;
            this.cost = cost;
        }
    }

    private static double nnTourFromDepot(double[][] dist, List<Integer> nodes) {
        if (nodes.isEmpty()) {
            return 0;
        }
        int[] starts = multistartNodes(dist, nodes);
        double best = INF;
        for (int start : starts) {
            List<Integer> tour = buildNnTour(dist, nodes, start);
            if (tour.size() != nodes.size()) {
                continue;
            }
            polishTour(dist, tour);
            best = Math.min(best, tourLengthFromDepot(dist, tour));
        }
        return best;
    }

    private static int[] multistartNodes(double[][] dist, List<Integer> nodes) {
        if (nodes.size() <= MULTISTART_CAP) {
            int[] all = new int[nodes.size()];
            for (int i = 0; i < nodes.size(); i++) {
                all[i] = nodes.get(i);
            }
            return all;
        }
        Integer[] sorted = nodes.toArray(new Integer[0]);
        Arrays.sort(sorted, Comparator.comparingDouble(j -> dist[0][j]));
        int k = MULTISTART_CAP;
        int[] starts = new int[k];
        for (int i = 0; i < k; i++) {
            starts[i] = sorted[i];
        }
        return starts;
    }

    private static void polishTour(double[][] dist, List<Integer> tour) {
        if (tour.size() < 2) {
            return;
        }
        twoOpt(dist, tour);
        reverseSegment(tour, 0, tour.size() - 1);
        twoOpt(dist, tour);
        orOpt1(dist, tour);
        twoOpt(dist, tour);
    }

    private static void orOpt1(double[][] dist, List<Integer> tour) {
        while (true) {
            int n = tour.size();
            if (n < 3) {
                return;
            }
            double bestLen = tourLengthFromDepot(dist, tour);
            List<Integer> bestTour = null;
            for (int i = 0; i < n; i++) {
                int node = tour.get(i);
                List<Integer> rest = new ArrayList<>(n - 1);
                for (int k = 0; k < n; k++) {
                    if (k != i) {
                        rest.add(tour.get(k));
                    }
                }
                for (int pos = 0; pos <= rest.size(); pos++) {
                    List<Integer> cand = new ArrayList<>(rest.size() + 1);
                    cand.addAll(rest.subList(0, pos));
                    cand.add(node);
                    cand.addAll(rest.subList(pos, rest.size()));
                    double len = tourLengthFromDepot(dist, cand);
                    if (len < bestLen - 1e-12) {
                        bestLen = len;
                        bestTour = cand;
                    }
                }
            }
            if (bestTour == null) {
                return;
            }
            tour.clear();
            tour.addAll(bestTour);
        }
    }

    private static List<Integer> buildNnTour(double[][] dist, List<Integer> nodes, int firstCustomer) {
        List<Integer> tour = new ArrayList<>(nodes.size());
        if (nodes.isEmpty()) {
            return tour;
        }
        boolean[] left = new boolean[dist.length];
        for (int j : nodes) {
            left[j] = true;
        }
        if (!left[firstCustomer]) {
            return tour;
        }
        tour.add(firstCustomer);
        left[firstCustomer] = false;
        int cur = firstCustomer;
        int rem = nodes.size() - 1;
        while (rem > 0) {
            int best = -1;
            double bestD = INF;
            for (int j = 1; j < left.length; j++) {
                if (!left[j]) {
                    continue;
                }
                double d = dist[cur][j];
                if (d < bestD) {
                    bestD = d;
                    best = j;
                }
            }
            if (best < 0) {
                return new ArrayList<>();
            }
            tour.add(best);
            left[best] = false;
            cur = best;
            rem--;
        }
        return tour;
    }

    private static double tourLengthFromDepot(double[][] dist, List<Integer> tour) {
        if (tour.isEmpty()) {
            return 0;
        }
        double total = dist[0][tour.get(0)];
        for (int i = 0; i + 1 < tour.size(); i++) {
            total += dist[tour.get(i)][tour.get(i + 1)];
        }
        total += dist[tour.get(tour.size() - 1)][0];
        return total;
    }

    private static void twoOpt(double[][] dist, List<Integer> tour) {
        int k = tour.size();
        if (k < 2) {
            return;
        }
        boolean improved = true;
        while (improved) {
            improved = false;
            double bestDelta = 0;
            int bestI = -1;
            int bestJ = -1;
            for (int i = 0; i < k; i++) {
                for (int j = i + 1; j < k; j++) {
                    double delta = twoOptDelta(dist, tour, i, j);
                    if (delta < bestDelta - 1e-12) {
                        bestDelta = delta;
                        bestI = i;
                        bestJ = j;
                    }
                }
            }
            if (bestI >= 0) {
                reverseSegment(tour, bestI, bestJ);
                improved = true;
            }
        }
    }

    private static double twoOptDelta(double[][] dist, List<Integer> tour, int i, int j) {
        int a = i == 0 ? 0 : tour.get(i - 1);
        int b = tour.get(i);
        int c = tour.get(j);
        int d = j + 1 < tour.size() ? tour.get(j + 1) : 0;
        return dist[a][c] + dist[b][d] - dist[a][b] - dist[c][d];
    }

    private static void reverseSegment(List<Integer> tour, int i, int j) {
        while (i < j) {
            int tmp = tour.get(i);
            tour.set(i, tour.get(j));
            tour.set(j, tmp);
            i++;
            j--;
        }
    }

    private static final class Instance {
        final int n;
        final int vehicles;
        final int cap;
        final int[] demand;
        final double[] x;
        final double[] y;

        Instance(int n, int vehicles, int cap, int[] demand, double[] x, double[] y) {
            this.n = n;
            this.vehicles = vehicles;
            this.cap = cap;
            this.demand = demand;
            this.x = x;
            this.y = y;
        }

        static Instance read(BufferedReader br) throws IOException {
            String line = br.readLine();
            while (line != null && line.trim().isEmpty()) {
                line = br.readLine();
            }
            if (line == null) {
                throw new IOException("empty input");
            }
            String[] head = line.trim().split("\\s+");
            int n = Integer.parseInt(head[0]);
            int v = Integer.parseInt(head[1]);
            int c = Integer.parseInt(head[2]);
            int[] demand = new int[n];
            double[] xs = new double[n];
            double[] ys = new double[n];
            for (int i = 0; i < n; i++) {
                line = br.readLine();
                while (line != null && line.trim().isEmpty()) {
                    line = br.readLine();
                }
                if (line == null) {
                    throw new IOException("unexpected EOF");
                }
                String[] p = line.trim().split("\\s+");
                demand[i] = Integer.parseInt(p[0]);
                xs[i] = Double.parseDouble(p[1]);
                ys[i] = Double.parseDouble(p[2]);
            }
            return new Instance(n, v, c, demand, xs, ys);
        }

        boolean feasible() {
            int maxD = 0;
            long sum = 0;
            for (int i = 1; i < n; i++) {
                maxD = Math.max(maxD, demand[i]);
                sum += demand[i];
            }
            if (maxD > cap) {
                return false;
            }
            return sum <= (long) vehicles * cap;
        }

        double[][] distances() {
            double[][] d = new double[n][n];
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    double dx = x[i] - x[j];
                    double dy = y[i] - y[j];
                    d[i][j] = Math.hypot(dx, dy);
                }
            }
            return d;
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            System.out.println(solveFromFile(args[0]));
        }
    }
}
