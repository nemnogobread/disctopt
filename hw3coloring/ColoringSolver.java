import java.io.*;
import java.util.*;

public class ColoringSolver {

    private static final int EXACT_N_LIMIT = 70;

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
        return solveDsatur(adj);
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

    private static int solveDsatur(int[][] adj) {
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

        return maxColor + 1;
    }
}
