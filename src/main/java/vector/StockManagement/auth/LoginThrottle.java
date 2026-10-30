package vector.StockManagement.auth;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import vector.StockManagement.exceptions.ApiException;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/** Slows down password guessing: after 5 wrong passwords an email + address is blocked for 15 minutes. */
@Component
public class LoginThrottle {
    private static final int MAX_FAILURES = 5;
    private static final Duration WINDOW = Duration.ofMinutes(15);

    private record Attempts(int failures, Instant first) {}

    private final ConcurrentHashMap<String, Attempts> attempts = new ConcurrentHashMap<>();

    public void check(String key) {
        Attempts a = attempts.get(key);
        if (a == null) return;
        if (a.first().plus(WINDOW).isBefore(Instant.now())) {
            attempts.remove(key);
            return;
        }
        if (a.failures() >= MAX_FAILURES) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too many failed attempts. Try again in 15 minutes.");
        }
    }

    public void failed(String key) {
        attempts.merge(key, new Attempts(1, Instant.now()),
                (old, fresh) -> old.first().plus(WINDOW).isBefore(Instant.now()) ? fresh : new Attempts(old.failures() + 1, old.first()));
    }

    public void succeeded(String key) {
        attempts.remove(key);
    }
}
