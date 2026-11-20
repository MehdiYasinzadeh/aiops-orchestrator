package ir.mehdiyasinzadeh.aiops.debounce;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Ultra-fast in-memory implementation of IncidentDebounceService using Caffeine.
 * Provides nanosecond-level lookups and automatic TTL eviction.
 */
@Service
@ConditionalOnProperty(name = "aiops.debounce.provider", havingValue = "caffeine", matchIfMissing = true)
public class CaffeineIncidentDebounceService implements IncidentDebounceService {

    private final Cache<String, Long> debounceCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(60))
            .maximumSize(50_000)
            .build();

    @Override
    public boolean shouldProcess(String errorSignature) {
        Long lastSeen = debounceCache.getIfPresent(errorSignature);
        if (lastSeen == null) {
            debounceCache.put(errorSignature, System.currentTimeMillis());
            return true;
        }
        return false;
    }

    @Override
    public void recordOccurrence(String errorSignature) {
        debounceCache.put(errorSignature, System.currentTimeMillis());
    }
}
