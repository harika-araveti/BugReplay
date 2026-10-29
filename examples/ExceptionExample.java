/**
 * ExceptionExample demonstrates a runtime error.
 * It simulates a student grade calculator dividing by zero student count.
 */
public class ExceptionExample {
    public static void main(String[] args) {
        System.out.println("ExceptionExample: Calculating average score...");
        int totalScore = 450;
        int studentCount = 0; // Bug: student count is zero

        // This triggers an unhandled ArithmeticException: / by zero
        int average = totalScore / studentCount;

        System.out.println("Average score: " + average);
    }
}
