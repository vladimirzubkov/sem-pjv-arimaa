package cz.cvut.fel.pjv.arimaa.model;

/**
 * Append-only audit entries for a match: draft edits during PLAY and committed turns.
 * Implementations are nested; no explicit {@code permits} clause needed in the same source file.
 */
public sealed interface GameHistoryEvent {

    /** A step was appended to the current PLAY turn draft. */
    record DraftStepAdded(int stepCountAfterAdd) implements GameHistoryEvent {}

    /** The last draft step was removed (undo within the current turn). */
    record DraftStepUndone(int remainingSteps) implements GameHistoryEvent {}

    /** A draft step was re-applied after an in-turn undo. */
    record DraftStepRedone(int stepCountAfterRedo) implements GameHistoryEvent {}

    /** Current PLAY draft was discarded via UI (steps cleared); may be restored by redo. */
    record DraftCleared() implements GameHistoryEvent {}

    /** A draft discarded by {@link DraftCleared} was restored. */
    record DraftRestoredAfterClear() implements GameHistoryEvent {}

    /** A full PLAY turn was applied and recorded on the timeline (notation line). */
    record TurnCommitted(String notationLine) implements GameHistoryEvent {}
}
