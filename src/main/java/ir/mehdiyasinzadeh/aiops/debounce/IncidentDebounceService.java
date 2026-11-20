package ir.mehdiyasinzadeh.aiops.debounce;

/**
 * Interface defining error debouncing and deduplication semantics.
 * Designed to allow seamless substitution between local in-memory (Caffeine)
 * and distributed (Redis) implementations.
 */
public interface IncidentDebounceService {

    /**
     * Determines whether an error signature should be processed for root cause analysis (RCA).
     *
     * @param errorSignature unique hash or signature of the exception and call site
     * @return true if this is the first occurrence within the debounce window; false if debounced
     */
    boolean shouldProcess(String errorSignature);

    /**
     * Records or updates the occurrence timestamp of an error signature.
     *
     * @param errorSignature unique hash or signature
     */
    void recordOccurrence(String errorSignature);
}
