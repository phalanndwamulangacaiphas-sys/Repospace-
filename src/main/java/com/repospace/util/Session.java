package com.repospace.util;

import com.repospace.model.User;

public final class Session {

    private static User currentUser;
    private static String theme = "Light";

    private Session() {
    }

    public static void setCurrentUser(User user) {
        currentUser = user;
    }

    public static User getCurrentUser() {
        return currentUser;
    }

    public static boolean isLoggedIn() {
        return currentUser != null;
    }

    public static String getTheme() {
        return theme;
    }

    public static void setTheme(String newTheme) {
        theme = newTheme == null ? "Light" : newTheme;
    }

    public static void clear() {
        currentUser = null;
        theme = "Light";
    }
}
