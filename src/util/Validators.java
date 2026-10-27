package util;

public final class Validators {

    private static final String MONEY_PATTERN = "\\d{1,8}(\\.\\d{1,2})?";

    private Validators() {
    }

    public static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        return value.trim();
    }

    public static int requireInt(String value, String fieldName, int min, int max) {
        String text = requireText(value, fieldName);
        try {
            int number = Integer.parseInt(text);
            if (number < min || number > max) {
                throw new IllegalArgumentException(fieldName + " must be between " + min + " and " + max + ".");
            }
            return number;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(fieldName + " must be a whole number.");
        }
    }

    public static double requireMoney(String value, String fieldName) {
        double amount = parseMoney(requireText(value, fieldName), fieldName);
        if (amount <= 0) {
            throw new IllegalArgumentException(fieldName + " must be greater than zero.");
        }
        return amount;
    }

    public static double optionalNonNegative(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            return 0;
        }
        return parseMoney(value.trim(), fieldName);
    }

    private static double parseMoney(String text, String fieldName) {
        if (text.startsWith("-")) {
            throw new IllegalArgumentException(fieldName + " cannot be negative.");
        }
        if (!text.matches(MONEY_PATTERN)) {
            throw new IllegalArgumentException(
                    fieldName + " must be an amount with at most 2 decimals (for example 12.50) and below 100,000,000.");
        }
        return Double.parseDouble(text);
    }

    public static double requireDoubleInRange(String value, String fieldName, double min, double max) {
        String text = requireText(value, fieldName);
        try {
            double number = Double.parseDouble(text);
            if (number < min || number > max) {
                throw new IllegalArgumentException(fieldName + " must be between " + min + " and " + max + ".");
            }
            return number;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(fieldName + " must be a valid number.");
        }
    }

    public static String optionalEmail(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String email = value.trim().toLowerCase();
        if (email.length() > 100 || !email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            throw new IllegalArgumentException("The e-mail address is not valid (for example name@example.com).");
        }
        return email;
    }

    public static String optional(String value) {
        return value == null ? "" : value.trim();
    }

    public static String requirePassword(String value) {
        PasswordUtil.checkPolicy(value);
        return value;
    }

}
