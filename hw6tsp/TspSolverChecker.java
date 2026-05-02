public class TspSolverChecker {

    public static void main(String[] args) throws Exception {

        String[] fileNames = {
            "./data/tsp_51_1",
            "./data/tsp_100_3",
            "./data/tsp_200_2",
            "./data/tsp_574_1",
            "./data/tsp_1889_1",
            "./data/tsp_33810_1",
        };

        for (String fileName : fileNames) {
            System.out.print(fileName + ": ");
            long result = TspSolver.solveFromFile(fileName);
            System.out.println(fileName + " " + result);
        }
    }
}