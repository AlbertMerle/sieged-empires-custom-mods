package com.siegedempires.util;

import java.util.regex.Pattern;

public class NameValidator {
    private static final Pattern VALID_NAME_PATTERN = Pattern.compile("^[a-zA-Z\\s]+$");
    private static final int MIN_LENGTH = 2;
    private static final int MAX_LENGTH = 40;

    public static boolean isValidTownName(String name) {
        return getValidationError(name) == null;
    }

    /** True if the name contains characters other than letters and spaces. */
    public static boolean hasDisallowedCharacters(String name) {
        if (name == null) {
            return true;
        }
        return !VALID_NAME_PATTERN.matcher(name.trim()).matches();
    }

    public static String getValidationError(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "Name cannot be empty";
        }
        
        String trimmed = name.trim();
        if (trimmed.length() < MIN_LENGTH) {
            return "Name must be at least " + MIN_LENGTH + " characters";
        }
        
        if (trimmed.length() > MAX_LENGTH) {
            return "Name cannot exceed " + MAX_LENGTH + " characters";
        }
        
        if (!VALID_NAME_PATTERN.matcher(trimmed).matches()) {
            return "Name can only contain letters and spaces";
        }
        
        return null;
    }

    public static String toId(String name) {
        return name.trim().toLowerCase().replaceAll("\\s+", "_");
    }
}