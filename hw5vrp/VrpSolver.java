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
        double a = solveGreedySequentialNn(demand, dist, n, V, c);
        if (a < INF / 2) {
            return a;
        }
        return solveGreedyFfdThenNn(demand, dist, n, V, c);
    }

    private static double solveGreedySequentialNn(int[] demand, double[][] dist, int n, int V, int c) {
        boolean[] assigned = new boolean[n];
        assigned[0] = true;
        int remaining = n - 1;
        double total = 0;
        int routes = 0;

        while (remaining > 0) {
            if (routes >= V) {
                return INF;
            }
            int cur = 0;
            int load = 0;
            boolean progress;
            do {
                progress = false;
                int best = -1;
                double bestD = INF;
                for (int j = 1; j < n; j++) {
                    if (assigned[j]) {
                        continue;
                    }
                    if (load + demand[j] > c) {
                        continue;
                    }
                    double d = dist[cur][j];
                    if (d < bestD) {
                        bestD = d;
                        best = j;
                    }
                }
                if (best < 0) {
                    break;
                }
                total += dist[cur][best];
                load += demand[best];
                assigned[best] = true;
                cur = best;
                remaining--;
                progress = true;
            } while (progress);

            if (cur != 0) {
                total += dist[cur][0];
            } else if (remaining > 0) {
                return INF;
            }
            routes++;
        }
        return total;
    }

    private static double solveGreedyFfdThenNn(int[] demand, double[][] dist, int n, int V, int c) {
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
            for (int v = 0; v < V; v++) {
                if (load[v] + demand[j] <= c) {
                    placed = v;
                    break;
                }
            }
            if (placed < 0) {
                return INF;
            }
            load[placed] += demand[j];
            truck[j] = placed;
        }

        @SuppressWarnings("unchecked")
        List<Integer>[] byTruck = new List[V];
        for (int v = 0; v < V; v++) {
            byTruck[v] = new ArrayList<>();
        }
        for (int j = 1; j < n; j++) {
            byTruck[truck[j]].add(j);
        }

        double total = 0;
        for (int v = 0; v < V; v++) {
            total += nnTourFromDepot(dist, byTruck[v]);
        }
        return total;
    }

    private static double nnTourFromDepot(double[][] dist, List<Integer> nodes) {
        if (nodes.isEmpty()) {
            return 0;
        }
        boolean[] left = new boolean[dist.length];
        for (int j : nodes) {
            left[j] = true;
        }
        double total = 0;
        int cur = 0;
        int rem = nodes.size();
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
                return INF;
            }
            total += dist[cur][best];
            left[best] = false;
            cur = best;
            rem--;
        }
        total += dist[cur][0];
        return total;
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
