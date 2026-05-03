public class VrpSolverChecker {

    public static void main(String[] args) throws Exception {

        String[] fileNames = {
            "./data/vrp_16_3_1",
            "./data/vrp_26_8_1",
            "./data/vrp_51_5_1",
            "./data/vrp_101_10_1",
            "./data/vrp_200_16_1",
            "./data/vrp_421_41_1"
        };

        for (String fileName : fileNames) {
            System.out.print(fileName + ": ");
            long result = VrpSolver.solveFromFile(fileName);
            System.out.println(fileName + " " + result);
        }
    }
}