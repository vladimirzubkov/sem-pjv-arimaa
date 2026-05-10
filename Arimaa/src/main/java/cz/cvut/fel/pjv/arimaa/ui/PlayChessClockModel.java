package cz.cvut.fel.pjv.arimaa.ui;

import cz.cvut.fel.pjv.arimaa.model.Game;
import cz.cvut.fel.pjv.arimaa.model.enums.GameState;
import cz.cvut.fel.pjv.arimaa.model.enums.PlayerSide;

/**
 * Per-side thinking time during {@link GameState#PLAY}; frozen after {@link GameState#GAME_OVER}. Not persisted.
 * Thread-safe for reads from a background ticker and updates from the JavaFX thread.
 */
public final class PlayChessClockModel {

    /** Display strings for the four numeric cells in the clock table. */
    public record ClockTableSnapshot(String goldTotal, String goldAvg, String silverTotal, String silverAvg) {}

    private final Object lock = new Object();

    private long goldAccumNanos;
    private long silverAccumNanos;
    private long segmentStartNanos;
    private PlayerSide chargingSide = PlayerSide.GOLD;
    /** {@code true} while a PLAY segment may run (excluding post-game freeze). */
    private boolean inPlayPhase;
    /** After game over: totals fixed, no live segment. */
    private boolean frozenGameOver;
    /** When non-null, live segment elapsed is frozen at {@code pauseStartedAtNanos - segmentStartNanos}. */
    private Long pauseStartedAtNanos;
    private int goldMovesCompleted;
    private int silverMovesCompleted;

    /** SETUP / menu: zero display, no charging. */
    public void resetToSetup() {
        synchronized (lock) {
            goldAccumNanos = 0;
            silverAccumNanos = 0;
            inPlayPhase = false;
            frozenGameOver = false;
            pauseStartedAtNanos = null;
            goldMovesCompleted = 0;
            silverMovesCompleted = 0;
        }
    }

    /** New PLAY phase from setup: both totals zero, segment for {@code sideToMove}. */
    public void enterPlayPhase(PlayerSide sideToMove) {
        synchronized (lock) {
            goldAccumNanos = 0;
            silverAccumNanos = 0;
            inPlayPhase = true;
            frozenGameOver = false;
            chargingSide = sideToMove;
            segmentStartNanos = System.nanoTime();
            pauseStartedAtNanos = null;
            goldMovesCompleted = 0;
            silverMovesCompleted = 0;
        }
    }

    /**
     * Closes the segment for {@code mover} (side that just completed a turn). After {@code gameOver}, clocks stop.
     */
    public void onTurnCommitted(PlayerSide mover, boolean gameOver) {
        synchronized (lock) {
            if (!inPlayPhase || frozenGameOver) {
                return;
            }
            long elapsed = segmentElapsedNanosLocked();
            addToSideInst(mover, elapsed);
            if (mover == PlayerSide.GOLD) {
                goldMovesCompleted++;
            } else {
                silverMovesCompleted++;
            }
            if (gameOver) {
                frozenGameOver = true;
                inPlayPhase = false;
                pauseStartedAtNanos = null;
            } else {
                chargingSide = mover == PlayerSide.GOLD ? PlayerSide.SILVER : PlayerSide.GOLD;
                segmentStartNanos = System.nanoTime();
                pauseStartedAtNanos = null;
            }
        }
    }

    /**
     * After loading a game or scrubbing notation: totals cleared; if PLAY, a fresh segment for current {@code sideToMove};
     * if GAME_OVER, show zeros (no live play segment).
     */
    public void resetForHistoryOrLoad(Game g) {
        synchronized (lock) {
            goldAccumNanos = 0;
            silverAccumNanos = 0;
            pauseStartedAtNanos = null;
            goldMovesCompleted = 0;
            silverMovesCompleted = 0;
            GameState st = g.getState();
            if (st == GameState.PLAY) {
                inPlayPhase = true;
                frozenGameOver = false;
                chargingSide = g.getSideToMove();
                segmentStartNanos = System.nanoTime();
            } else if (st == GameState.GAME_OVER) {
                inPlayPhase = false;
                frozenGameOver = true;
            } else {
                inPlayPhase = false;
                frozenGameOver = false;
            }
        }
    }

    /**
     * When {@code suspend} is {@code true}, elapsed time in the current segment stops increasing until resumed
     * (CPU autoplay pause during computer's turn).
     */
    public void setCpuChargeSuspended(boolean suspend) {
        synchronized (lock) {
            if (!inPlayPhase || frozenGameOver) {
                return;
            }
            if (suspend) {
                if (pauseStartedAtNanos == null) {
                    pauseStartedAtNanos = System.nanoTime();
                }
            } else {
                if (pauseStartedAtNanos != null) {
                    long pausedFor = System.nanoTime() - pauseStartedAtNanos;
                    segmentStartNanos += pausedFor;
                    pauseStartedAtNanos = null;
                }
            }
        }
    }

    /** Totals include the live segment; averages use completed segments only ({@code accum} / move count). */
    public ClockTableSnapshot snapshotClockTable() {
        synchronized (lock) {
            if (!inPlayPhase && !frozenGameOver) {
                return new ClockTableSnapshot("00:00", "—", "00:00", "—");
            }
            long gTotal = goldAccumNanos;
            long sTotal = silverAccumNanos;
            if (!frozenGameOver) {
                long seg = segmentElapsedNanosLocked();
                if (chargingSide == PlayerSide.GOLD) {
                    gTotal += seg;
                } else {
                    sTotal += seg;
                }
            }
            return new ClockTableSnapshot(
                    formatMmSs(gTotal),
                    formatAvg(goldAccumNanos, goldMovesCompleted),
                    formatMmSs(sTotal),
                    formatAvg(silverAccumNanos, silverMovesCompleted));
        }
    }

    private long segmentElapsedNanosLocked() {
        if (!inPlayPhase || frozenGameOver) {
            return 0;
        }
        if (pauseStartedAtNanos != null) {
            return pauseStartedAtNanos - segmentStartNanos;
        }
        return System.nanoTime() - segmentStartNanos;
    }

    private void addToSideInst(PlayerSide side, long nanos) {
        if (side == PlayerSide.GOLD) {
            goldAccumNanos += nanos;
        } else {
            silverAccumNanos += nanos;
        }
    }

    private static String formatMmSs(long nanos) {
        long sec = Math.max(0L, nanos / 1_000_000_000L);
        long m = sec / 60;
        long s = sec % 60;
        return "%02d:%02d".formatted(m, s);
    }

    private static String formatAvg(long completedNanos, int moves) {
        if (moves <= 0) {
            return "—";
        }
        return formatMmSs(completedNanos / moves);
    }
}
