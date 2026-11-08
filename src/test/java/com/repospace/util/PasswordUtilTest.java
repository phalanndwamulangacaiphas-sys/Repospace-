package com.repospace.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class PasswordUtilTest {

    @Test
    public void correctPasswordIsAccepted() {
        String salt = PasswordUtil.generateSalt();
        String hash = PasswordUtil.hash("correct horse", salt);
        assertTrue(PasswordUtil.verify("correct horse", salt, hash));
    }

    @Test
    public void wrongPasswordIsRejected() {
        String salt = PasswordUtil.generateSalt();
        String hash = PasswordUtil.hash("correct horse", salt);
        assertFalse(PasswordUtil.verify("Correct horse", salt, hash));
        assertFalse(PasswordUtil.verify("", salt, hash));
    }

    @Test
    public void samePasswordWithDifferentSaltGivesDifferentHash() {
        String hashA = PasswordUtil.hash("secret123", PasswordUtil.generateSalt());
        String hashB = PasswordUtil.hash("secret123", PasswordUtil.generateSalt());
        assertNotEquals(hashA, hashB);
    }

    @Test
    public void hashIsDeterministicForTheSameSalt() {
        String salt = PasswordUtil.generateSalt();
        assertEquals(PasswordUtil.hash("abc12345", salt), PasswordUtil.hash("abc12345", salt));
    }

    @Test
    public void nullInputsAreRejectedSafely() {
        assertFalse(PasswordUtil.verify(null, "salt", "hash"));
        assertFalse(PasswordUtil.verify("pw", null, "hash"));
        assertFalse(PasswordUtil.verify("pw", "salt", null));
    }
}
