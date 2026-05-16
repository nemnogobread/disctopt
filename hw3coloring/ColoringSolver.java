import java.io.*;
import java.util.*;

public class ColoringSolver {

    private static final int EXACT_N_LIMIT = 70;
    private static final long HEURISTIC_TIME_NS = 10L * 60L * 1_000_000_000L;

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
        int[] data = readAllInts(br);
        if (data.length < 2) {
            return -1L;
        }

        int n = data[0];
        int m = data[1];

        int[][] adj = new int[n][];
        int[] deg = new int[n];
        for (int i = 0; i < m; i++) {
            int u = data[2 + 2 * i];
            int v = data[2 + 2 * i + 1];
            if (u < 0 || u >= n || v < 0 || v >= n || u == v) {
                continue;
            }
            deg[u]++;
            deg[v]++;
        }

        int[][] tmp = new int[n][];
        for (int i = 0; i < n; i++) {
            tmp[i] = new int[deg[i]];
            deg[i] = 0;
        }
        for (int i = 0; i < m; i++) {
            int u = data[2 + 2 * i];
            int v = data[2 + 2 * i + 1];
            if (u < 0 || u >= n || v < 0 || v >= n || u == v) {
                continue;
            }
            tmp[u][deg[u]++] = v;
            tmp[v][deg[v]++] = u;
        }
        adj = tmp;

        if (n <= EXACT_N_LIMIT) {
            return solveExactBySubsetDp(adj);
        }
        int[] color = dsaturColoring(adj);
        long deadline = System.nanoTime() + HEURISTIC_TIME_NS;
        localSearchImprove(adj, color, deadline);
        return countUsedColors(color);
    }

    private static int[] readAllInts(BufferedReader br) throws IOException {
        ArrayList<Integer> vals = new ArrayList<>();
        String line;
        while ((line = br.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] parts = line.split("\\s+");
            for (String p : parts) {
                vals.add(Integer.parseInt(p));
            }
        }
        int[] a = new int[vals.size()];
        for (int i = 0; i < vals.size(); i++) {
            a[i] = vals.get(i);
        }
        return a;
    }

    private static int solveExactBySubsetDp(int[][] adj) {
        int n = adj.length;
        int fullMask = (1 << n) - 1;

        int[] neighMask = new int[n];
        for (int u = 0; u < n; u++) {
            int mask = 0;
            for (int v : adj[u]) {
                if (v >= 0 && v < n) {
                    mask |= (1 << v);
                }
            }
            neighMask[u] = mask;
        }

        boolean[] independent = new boolean[1 << n];
        independent[0] = true;
        for (int mask = 1; mask <= fullMask; mask++) {
            int vBit = mask & -mask;
            int v = Integer.numberOfTrailingZeros(vBit);
            int rest = mask ^ vBit;
            independent[mask] = independent[rest] && ((rest & neighMask[v]) == 0);
        }

        int INF = n + 1;
        int[] dp = new int[1 << n];
        Arrays.fill(dp, INF);
        dp[0] = 0;

        for (int mask = 1; mask <= fullMask; mask++) {
            int pivotBit = mask & -mask;
            for (int sub = mask; sub > 0; sub = (sub - 1) & mask) {
                if ((sub & pivotBit) == 0) {
                    continue;
                }
                if (!independent[sub]) {
                    continue;
                }
                int candidate = dp[mask ^ sub] + 1;
                if (candidate < dp[mask]) {
                    dp[mask] = candidate;
                }
            }
        }
        return dp[fullMask];
    }

    private static int[] dsaturColoring(int[][] adj) {
        int n = adj.length;
        int[] degree = new int[n];
        for (int i = 0; i < n; i++) {
            degree[i] = adj[i].length;
        }

        int[] color = new int[n];
        Arrays.fill(color, -1);

        int[] saturation = new int[n];
        boolean[][] seenColorAtVertex = new boolean[n][n];

        int[] mark = new int[n];
        int stamp = 1;
        int maxColor = -1;

        for (int step = 0; step < n; step++) {
            int u = -1;
            int bestSat = -1;
            int bestDeg = -1;

            for (int v = 0; v < n; v++) {
                if (color[v] != -1) {
                    continue;
                }
                int sat = saturation[v];
                int deg = degree[v];
                if (sat > bestSat || (sat == bestSat && (deg > bestDeg || (deg == bestDeg && v < u)))) {
                    bestSat = sat;
                    bestDeg = deg;
                    u = v;
                }
            }

            stamp++;
            for (int v : adj[u]) {
                if (color[v] >= 0) {
                    mark[color[v]] = stamp;
                }
            }

            int c = 0;
            while (c < n && mark[c] == stamp) {
                c++;
            }
            color[u] = c;

            if (c > maxColor) {
                maxColor = c;
            }

            for (int v : adj[u]) {
                if (color[v] == -1 && !seenColorAtVertex[v][c]) {
                    seenColorAtVertex[v][c] = true;
                    saturation[v]++;
                }
            }
        }

        return color;
    }

    private static int countUsedColors(int[] color) {
        int max = -1;
        for (int c : color) {
            if (c > max) {
                max = c;
            }
        }
        return max + 1;
    }

    private static void compressColors(int[] color) {
        int max = -1;
        for (int c : color) {
            if (c > max) {
                max = c;
            }
        }
        if (max < 0) {
            return;
        }
        boolean[] seen = new boolean[max + 1];
        for (int c : color) {
            seen[c] = true;
        }
        int[] map = new int[max + 1];
        int t = 0;
        for (int c = 0; c <= max; c++) {
            if (seen[c]) {
                map[c] = t++;
            }
        }
        for (int i = 0; i < color.length; i++) {
            color[i] = map[color[i]];
        }
    }

    private static void localSearchImprove(int[][] adj, int[] color, long deadlineNs) {
        int n = color.length;
        Random rnd = new Random(0xC011_7E5L);
        int[] order = new int[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }

        int[] neighborHasColor = new int[n + 1];
        int[] stampRef = new int[]{1};

        int best = countUsedColors(color);
        int idle = 0;
        final int maxIdleBeforeKick = 28;
        int kempePerRound = Math.min(8000, Math.max(80, n + n / 2));
        int kempeKick = Math.min(12000, Math.max(200, n * 2));

        while (System.nanoTime() < deadlineNs) {
            shuffle(order, rnd);
            greedyMinimalColorSweep(adj, color, order, neighborHasColor, stampRef);
            compressColors(color);

            kempeRandomPerturbations(adj, color, rnd, kempePerRound);
            compressColors(color);

            tryLowerMaxColorVertices(adj, color, rnd, neighborHasColor, stampRef);

            shuffle(order, rnd);
            greedyMinimalColorSweep(adj, color, order, neighborHasColor, stampRef);
            compressColors(color);

            tryShiftNonMaxToSmaller(adj, color, rnd, neighborHasColor, stampRef);
            compressColors(color);

            int now = countUsedColors(color);
            if (now < best) {
                best = now;
                idle = 0;
            } else {
                idle++;
            }

            if (idle >= maxIdleBeforeKick) {
                kempeRandomPerturbations(adj, color, rnd, kempeKick);
                compressColors(color);
                idle = 0;
            }
        }
    }

    private static void greedyMinimalColorSweep(int[][] adj, int[] color, int[] order,
            int[] blocked, int[] stampRef) {
        int n = color.length;
        int lim = n;
        int stamp = stampRef[0];
        for (int u : order) {
            stamp++;
            for (int v : adj[u]) {
                int cv = color[v];
                if (cv >= 0 && cv < lim) {
                    blocked[cv] = stamp;
                }
            }
            int c = 0;
            while (c < lim && blocked[c] == stamp) {
                c++;
            }
            color[u] = c;
        }
        stampRef[0] = stamp;
    }

    private static void kempeSwapComponentContaining(int[][] adj, int[] color, int v, int i, int j) {
        if (i == j) {
            return;
        }
        int n = color.length;
        if (color[v] != i) {
            return;
        }
        boolean[] inComp = new boolean[n];
        ArrayDeque<Integer> dq = new ArrayDeque<>();
        inComp[v] = true;
        dq.add(v);
        while (!dq.isEmpty()) {
            int u = dq.poll();
            for (int w : adj[u]) {
                int cw = color[w];
                if ((cw == i || cw == j) && !inComp[w]) {
                    inComp[w] = true;
                    dq.add(w);
                }
            }
        }
        for (int u = 0; u < n; u++) {
            if (!inComp[u]) {
                continue;
            }
            if (color[u] == i) {
                color[u] = j;
            } else if (color[u] == j) {
                color[u] = i;
            }
        }
    }

    private static void kempeRandomPerturbations(int[][] adj, int[] color, Random rnd, int numMoves) {
        int n = color.length;
        for (int t = 0; t < numMoves; t++) {
            int k = countUsedColors(color);
            if (k < 2) {
                return;
            }
            int v = rnd.nextInt(n);
            int i = color[v];
            int j = rnd.nextInt(k);
            while (j == i) {
                j = rnd.nextInt(k);
            }
            kempeSwapComponentContaining(adj, color, v, i, j);
        }
    }

    private static void tryLowerMaxColorVertices(int[][] adj, int[] color, Random rnd,
            int[] blocked, int[] stampRef) {
        int n = color.length;
        ArrayList<Integer> high = new ArrayList<>();
        ArrayList<Integer> feas = new ArrayList<>();
        for (int rep = 0; rep < 20; rep++) {
            int maxC = -1;
            for (int c : color) {
                if (c > maxC) {
                    maxC = c;
                }
            }
            if (maxC <= 0) {
                return;
            }
            high.clear();
            for (int u = 0; u < n; u++) {
                if (color[u] == maxC) {
                    high.add(u);
                }
            }
            if (high.isEmpty()) {
                return;
            }
            Collections.shuffle(high, rnd);
            int stamp = stampRef[0];
            boolean moved = false;
            for (int u : high) {
                stamp++;
                for (int v : adj[u]) {
                    int cv = color[v];
                    if (cv >= 0 && cv < maxC) {
                        blocked[cv] = stamp;
                    }
                }
                feas.clear();
                for (int c = 0; c < maxC; c++) {
                    if (blocked[c] != stamp) {
                        feas.add(c);
                    }
                }
                if (!feas.isEmpty()) {
                    color[u] = feas.get(rnd.nextInt(feas.size()));
                    moved = true;
                }
            }
            stampRef[0] = stamp;
            compressColors(color);
            if (!moved) {
                break;
            }
        }
    }

    private static void tryShiftNonMaxToSmaller(int[][] adj, int[] color, Random rnd,
            int[] blocked, int[] stampRef) {
        int n = color.length;
        int maxC = -1;
        for (int c : color) {
            if (c > maxC) {
                maxC = c;
            }
        }
        if (maxC <= 0) {
            return;
        }
        int stamp = stampRef[0];
        ArrayList<Integer> verts = new ArrayList<>();
        for (int u = 0; u < n; u++) {
            if (color[u] < maxC) {
                verts.add(u);
            }
        }
        Collections.shuffle(verts, rnd);
        for (int u : verts) {
            stamp++;
            for (int v : adj[u]) {
                int cv = color[v];
                if (cv >= 0 && cv < maxC) {
                    blocked[cv] = stamp;
                }
            }
            int c = 0;
            while (c < maxC && blocked[c] == stamp) {
                c++;
            }
            if (c < maxC) {
                color[u] = c;
            }
        }
        stampRef[0] = stamp;
    }

    private static void shuffle(int[] a, Random rnd) {
        for (int i = a.length - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            int t = a[i];
            a[i] = a[j];
            a[j] = t;
        }
    }
}
