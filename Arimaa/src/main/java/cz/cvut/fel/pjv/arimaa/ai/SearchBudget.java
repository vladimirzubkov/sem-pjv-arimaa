package cz.cvut.fel.pjv.arimaa.ai;

/** Wall-clock cap for CPU move selection (levels 1–2) so dense positions do not block the worker thread. */
final class SearchBudget {

    private final long deadlineNs;

    SearchBudget(long maxMs) {
        long ms = Math.max(200L, maxMs);
        this.deadlineNs = System.nanoTime() + ms * 1_000_000L;
    }

    boolean isExpired() {
        return System.nanoTime() >= deadlineNs;
    }
}
