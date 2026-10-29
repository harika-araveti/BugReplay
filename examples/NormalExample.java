/**
 * NormalExample is a predictable demo program.
 * It computes the sum of numbers 1 to 5 and prints the progress.
 */
public class NormalExample {
    public static void main(String[] args) {
        System.out.println("NormalExample: Starting execution...");
        int sum = 0;
        for (int i = 1; i <= 5; i++) {
            sum += i;
            System.out.println("Adding " + i + ", current sum = " + sum);
        }
        System.out.println("NormalExample: Finished successfully. Total sum = " + sum);
    }
}
