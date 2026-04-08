public class ColoringSolverChecker {

    public static void main(String[] args) throws Exception {

        String[] fileNames = {
            "./data/gc_50_3",
            "./data/gc_70_7",
            "./data/gc_100_5",
            "./data/gc_250_9",
            "./data/gc_500_1",
            "./data/gc_1000_5"
        };

        for (String fileName : fileNames) {
            System.out.print(fileName + ": ");
            long cost = ColoringSolver.solveFromFile(fileName);
            System.out.println(cost);
        }
    }
}