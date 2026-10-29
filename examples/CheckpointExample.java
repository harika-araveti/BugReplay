/**
 * CheckpointExample demonstrates explicit internal state checkpoints in BugReplay.
 *
 * Supported programs emit structured checkpoints to stdout using the prefix
 * BUGREPLAY_CHECKPOINT:name=<name>;<key>=<value>...
 *
 * BugReplay's TraceRecorder automatically detects these lines, parses the variables,
 * and packages them into rich CHECKPOINT trace events.
 */
public class CheckpointExample {

    public static void main(String[] args) {
        System.out.println("Starting Bank Account Transaction Service...");

        String accountId = "ACC-101";
        double balance = 1000.00;

        // Checkpoint 1: Initial state
        System.out.println("BUGREPLAY_CHECKPOINT:name=INITIAL_STATE;account=" + accountId + ";balance=" + balance + ";status=OPEN");

        // Action 1: Deposit
        double deposit = 500.00;
        balance += deposit;
        System.out.println("Processed deposit of $" + deposit + ". New balance: $" + balance);

        // Checkpoint 2: After deposit
        System.out.println("BUGREPLAY_CHECKPOINT:name=AFTER_DEPOSIT;account=" + accountId + ";balance=" + balance + ";depositAmount=" + deposit);

        // Action 2: Withdrawal
        double withdrawal = 300.00;
        balance -= withdrawal;
        System.out.println("Processed withdrawal of $" + withdrawal + ". Remaining balance: $" + balance);

        // Checkpoint 3: Final state
        System.out.println("BUGREPLAY_CHECKPOINT:name=FINAL_STATE;account=" + accountId + ";balance=" + balance + ";transactionsCount=2");

        System.out.println("Transaction session finished successfully.");
    }
}
