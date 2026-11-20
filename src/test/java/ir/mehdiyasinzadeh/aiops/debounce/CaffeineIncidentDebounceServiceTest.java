package ir.mehdiyasinzadeh.aiops.debounce;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaffeineIncidentDebounceServiceTest {

    private CaffeineIncidentDebounceService debounceService;

    @BeforeEach
    void setUp() {
        debounceService = new CaffeineIncidentDebounceService();
    }

    @Test
    @DisplayName("First occurrence of an error signature should be processed")
    void shouldProcessFirstOccurrence() {
        String signature = "NullPointerException@OrderService.processPayment";
        assertTrue(debounceService.shouldProcess(signature), "First occurrence must return true");
    }

    @Test
    @DisplayName("Repeated occurrence within debounce window should be filtered")
    void shouldFilterRepeatedOccurrence() {
        String signature = "SQLException@DatabasePool.getConnection";
        assertTrue(debounceService.shouldProcess(signature), "Initial occurrence should pass");
        assertFalse(debounceService.shouldProcess(signature), "Second occurrence within window must be filtered");
        assertFalse(debounceService.shouldProcess(signature), "Third occurrence within window must be filtered");
    }

    @Test
    @DisplayName("Different error signatures should be tracked independently")
    void shouldTrackDifferentSignaturesIndependently() {
        String sigA = "TimeoutException@PaymentGateway.call";
        String sigB = "AuthException@UserSession.validate";

        assertTrue(debounceService.shouldProcess(sigA));
        assertTrue(debounceService.shouldProcess(sigB));
        assertFalse(debounceService.shouldProcess(sigA));
        assertFalse(debounceService.shouldProcess(sigB));
    }
}
