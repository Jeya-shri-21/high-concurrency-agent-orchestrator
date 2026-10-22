package com.zaalima.orchestrator.lock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LockToken Unit Tests")
class LockTokenTest {

    @Test
    @DisplayName("Should create valid LockToken and expose attributes correctly")
    void testValidLockToken() {
        Instant now = Instant.now();
        Instant expires = now.plus(30, ChronoUnit.SECONDS);

        LockToken token = new LockToken("lock:task:123", "node-1:vt-1:uuid", now, expires);

        assertEquals("lock:task:123", token.lockKey());
        assertEquals("node-1:vt-1:uuid", token.ownerToken());
        assertEquals(now, token.acquiredAt());
        assertEquals(expires, token.expiresAt());
        assertFalse(token.isExpired());
    }

    @Test
    @DisplayName("Should detect expired token")
    void testExpiredLockToken() {
        Instant past = Instant.now().minus(10, ChronoUnit.SECONDS);
        Instant expiredAt = Instant.now().minus(5, ChronoUnit.SECONDS);

        LockToken token = new LockToken("lock:task:123", "owner", past, expiredAt);
        assertTrue(token.isExpired());
    }

    @Test
    @DisplayName("Should reject null fields")
    void testNullGuards() {
        Instant now = Instant.now();
        assertThrows(NullPointerException.class, () -> new LockToken(null, "owner", now, now.plusSeconds(10)));
        assertThrows(NullPointerException.class, () -> new LockToken("key", null, now, now.plusSeconds(10)));
        assertThrows(NullPointerException.class, () -> new LockToken("key", "owner", null, now.plusSeconds(10)));
        assertThrows(NullPointerException.class, () -> new LockToken("key", "owner", now, null));
    }

    @Test
    @DisplayName("Should reject expiresAt before acquiredAt")
    void testInvalidExpirationOrder() {
        Instant now = Instant.now();
        Instant past = now.minusSeconds(1);

        assertThrows(IllegalArgumentException.class, () -> new LockToken("key", "owner", now, past));
    }
}
