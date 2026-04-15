public class FacilitySolverChecker {

    public static void main(String[] args) throws Exception {

        String[] fileNames = {
            "./data/fl_25_2",
            "./data/fl_100_1",
            "./data/fl_200_7",
            "./data/fl_500_7",
            "./data/fl_1000_2",
            "./data/fl_2000_2"
        };

        for (String fileName : fileNames) {
            System.out.print(fileName + ": ");
            long result = FacilitySolver.solveFromFile(fileName);
            System.out.println(fileName + " " + result);
        }
    }
}