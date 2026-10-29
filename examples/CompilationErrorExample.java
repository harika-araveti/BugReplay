/**
 * CompilationErrorExample intentionally contains a syntax error
 * to test compilation failure handling in BugReplay.
 */
public class CompilationErrorExample {
    public static void main(String[] args) {
        System.out.println("This program has a missing semicolon") // syntax error: missing semicolon
        int x = 42;
    }
}
