/**
 * TimeoutExample simulates a program stuck in an infinite loop
 * or prolonged operation to test timeout handling and forced termination.
 */
public class TimeoutExample {
    public static void main(String[] args) {
        System.out.println("TimeoutExample: Starting an infinite loop...");
        int count = 0;
        while (true) {
            count++;
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                System.out.println("TimeoutExample: Interrupted, exiting...");
                break;
            }
            if (count % 5 == 0) {
                System.out.println("TimeoutExample: Still running... count=" + count);
            }
        }
    }
}
