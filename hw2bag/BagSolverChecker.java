public class BagSolverChecker {

    public static void main(String[] args) throws Exception {
        String[] fileNames = {
            "./data/ks_30_0",
            "./data/ks_50_0",
            "./data/ks_200_0",
            "./data/ks_400_0",
            "./data/ks_1000_0",
            "./data/ks_10000_0"
        };

        for (String fileName : fileNames) {
            System.out.print(fileName + ": ");
            long cost = BagSolver.solveFromFile(fileName);
            System.out.println(cost);
        }
    }
}