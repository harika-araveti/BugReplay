/**
 * FixedDiscount fixes the integer division logic bug from BuggyDiscount.
 *
 * Using a floating-point divisor (100.0) ensures accurate percentage calculation.
 */
public class FixedDiscount {

    public static void main(String[] args) {
        System.out.println("Starting Bookstore Checkout Service (Fixed Version)...");

        double subtotal = 100.00;
        int discountPercent = 15;

        // Checkpoint 1: Initial cart
        System.out.println("BUGREPLAY_CHECKPOINT:name=CART_INITIALIZED;subtotal=" + subtotal + ";couponPercent=" + discountPercent);

        // Fixed: Use 100.0 (double) to prevent integer truncation
        double discountAmount = subtotal * (discountPercent / 100.0);
        double finalTotal = subtotal - discountAmount;

        // Checkpoint 2: Order submitted
        System.out.println("BUGREPLAY_CHECKPOINT:name=ORDER_SUBMITTED;subtotal=" + subtotal + ";discountApplied=" + discountAmount + ";finalTotal=" + finalTotal);

        System.out.println("Order processed: Subtotal: $" + subtotal + ", Discount: $" + discountAmount + ", Final Total: $" + finalTotal);
    }
}
