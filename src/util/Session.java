package util;

import model.User;

public final class Session {

    private static volatile User currentUser;

    private static volatile Runnable onUserChanged;

    private Session() {
    }

    public static void setOnUserChanged(Runnable listener) {
        onUserChanged = listener;
    }

    public static void notifyUserChanged() {
        Runnable listener = onUserChanged;
        if (listener != null) {
            listener.run();
        }
    }

    public static void setUser(User user) {
        currentUser = user;
    }

    public static User getUser() {
        return currentUser;
    }

    public static String getRole() {
        User user = currentUser;
        return user == null ? "" : user.getRole();
    }

    public static boolean hasRole(String... roles) {
        User user = currentUser;
        if (user == null) {
            return false;
        }
        for (String role : roles) {
            if (role.equalsIgnoreCase(user.getRole())) {
                return true;
            }
        }
        return false;
    }

    public static boolean isAdmin() {
        return hasRole("OWNER", "ADMIN");
    }

    public static void clear() {
        currentUser = null;
        onUserChanged = null;
    }
}
