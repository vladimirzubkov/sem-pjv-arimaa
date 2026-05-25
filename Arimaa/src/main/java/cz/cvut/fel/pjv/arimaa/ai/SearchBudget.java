package cz.cvut.fel.pjv.arimaa.ai;

/** Wall-clock cap for CPU move selection (levels 1–2) so dense positions do not block the worker thread. */
final class SearchBudget {

    private static final int TIME_CHECK_MASK = 1023;

    private final long deadlineNs;
    private int tickCounter;
    private boolean expired;

    /** Caps search time at least 200 ms; used by greedy and alpha-beta CPU levels. */
    SearchBudget(long maxMs) {
        long ms = Math.max(200L, maxMs);
        this.deadlineNs = System.nanoTime() + ms * 1_000_000L;
    }

    /**
     * Cheap periodic wall-clock check (masked counter); used inside move search loops to abort before UI stalls.
     */
    boolean isExpired() {
        if (expired) {
            return true;
        }
        if ((tickCounter++ & TIME_CHECK_MASK) != 0) {
            return false;
        }
        if (System.nanoTime() >= deadlineNs) {
            expired = true;
            return true;
        }
        return false;
    }
}
