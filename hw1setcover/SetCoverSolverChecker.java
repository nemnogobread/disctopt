public class SetCoverSolverChecker {

    public static void main(String[] args) throws Exception {
        String[] fileNames = {
            "./data/sc_157_0",
            "./data/sc_330_0",
            "./data/sc_1000_11",
            "./data/sc_5000_1",
            "./data/sc_10000_5",
            "./data/sc_10000_2"
        };

        for (String fileName : fileNames) {
            System.out.print(fileName + ": ");
            long cost = SetCoverSolver.solveFromFile(fileName);
            System.out.println(cost);
        }
    }
}