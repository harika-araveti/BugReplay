/**
 * BuggyDiscount demonstrates a common logical bug in Java: integer division.
 *
 * When calculating a 15% discount as (discountPercent / 100), the expression
 * evaluates to 0 because both operands are integers.
 */
public class BuggyDiscount {

    public static void main(String[] args) {
        System.out.println("Starting Bookstore Checkout Service (Buggy Version)...");

        double subtotal = 100.00;
        int discountPercent = 15;

        // Checkpoint 1: Initial cart
        System.out.println("BUGREPLAY_CHECKPOINT:name=CART_INITIALIZED;subtotal=" + subtotal + ";couponPercent=" + discountPercent);

        // Logical Bug: (discountPercent / 100) performs integer division (15 / 100 = 0)
        double discountAmount = subtotal * (discountPercent / 100);
        double finalTotal = subtotal - discountAmount;

        // Checkpoint 2: Order submitted
        System.out.println("BUGREPLAY_CHECKPOINT:name=ORDER_SUBMITTED;subtotal=" + subtotal + ";discountApplied=" + discountAmount + ";finalTotal=" + finalTotal);

        System.out.println("Order processed: Subtotal: $" + subtotal + ", Discount: $" + discountAmount + ", Final Total: $" + finalTotal);
    }
}
