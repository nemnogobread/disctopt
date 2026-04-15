import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;

public class FacilitySolver {

    private static final double EPS = 1e-9;
    private static final double INF = 1e100;

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

    private static double solveHeuristic(Instance inst, double[][] dist) {
        int m = inst.m;
        Integer[] byDemand = new Integer[m];
        for (int i = 0; i < m; i++) {
            byDemand[i] = i;
        }
        Arrays.sort(byDemand, (a, b) -> Double.compare(inst.demand[b], inst.demand[a]));
        int[] demandOrder = new int[m];
        for (int i = 0; i < m; i++) {
            demandOrder[i] = byDemand[i];
        }
        Solution greedy = greedyConstruct(inst, dist, demandOrder);
        Solution fallback = allOpenFallback(inst, dist);

        double best = INF;
        if (greedy != null) {
            best = Math.min(best, greedy.totalCost(inst, dist));
        }
        if (fallback != null) {
            best = Math.min(best, fallback.totalCost(inst, dist));
        }
        return best < INF / 2 ? best : -1;
    }

    private static Solution allOpenFallback(Instance inst, double[][] dist) {
        int n = inst.n;
        int m = inst.m;
        boolean[] open = new boolean[n];
        Arrays.fill(open, true);
        double[] load = new double[n];
        int[] assign = new int[m];
        Arrays.fill(assign, -1);
        Integer[] ord = new Integer[m];
        for (int i = 0; i < m; i++) {
            ord[i] = i;
        }
        Arrays.sort(ord, (a, b) -> Double.compare(inst.demand[b], inst.demand[a]));
        for (int idx = 0; idx < m; idx++) {
            int c = ord[idx];
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
