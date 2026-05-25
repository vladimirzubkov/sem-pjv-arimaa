package cz.cvut.fel.pjv.arimaa.ai;

import cz.cvut.fel.pjv.arimaa.model.DefaultRuleEngine;
import cz.cvut.fel.pjv.arimaa.model.Game;
import cz.cvut.fel.pjv.arimaa.model.GameMemento;
import cz.cvut.fel.pjv.arimaa.model.Move;
import cz.cvut.fel.pjv.arimaa.model.Step;
import cz.cvut.fel.pjv.arimaa.model.enums.GameState;
import cz.cvut.fel.pjv.arimaa.model.enums.PlayerSide;
import cz.cvut.fel.pjv.arimaa.model.enums.StepKind;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

/**
 * Level-1 CPU: under a wall-clock budget, repeatedly samples legal full turns (randomized DFS), scores each distinct
 * candidate with {@link HeuristicEvaluation}, and picks a best one (random tie-break). Avoids enumerating all legal
 * turns so dense positions do not stall before scoring begins.
 */
public final class GreedyComputerMove {

    /** Wall-clock budget for sampling + scoring (enumeration is not used). */
    private static final long SEARCH_BUDGET_MS = 2500;

    /** Upper bound on sample attempts so duplicate-heavy positions cannot spin if the budget were raised. */
    private static final int MAX_SAMPLES = 12000;

    private GreedyComputerMove() {}

    /**
     * Samples distinct legal turns under a time budget, scores them for the mover, then returns an engine-legal copy.
     * Called from {@link ComputerPlayMove} for level-1 CPU.
     */
    public static Move chooseMove(Game game, Random random) {
        Objects.requireNonNull(game, "game");
        Objects.requireNonNull(random, "random");
        if (game.getState() != GameState.PLAY) {
            throw new IllegalStateException("PLAY only");
        }
        if (!DefaultRuleEngine.existsLegalTurn(game)) {
            throw new IllegalStateException("no legal complete moves");
        }

        GameMemento baseline = CpuMoveSupport.baseline(game);
        PlayerSide root = game.getSideToMove();
        SearchSession session = SearchSession.fromGame(game);
        SearchBudget budget = new SearchBudget(SEARCH_BUDGET_MS);
        Set<String> seen = new HashSet<>();
        double bestScore = -Double.MAX_VALUE;
        List<Move> tied = new ArrayList<>();

        for (int n = 0; n < MAX_SAMPLES && !budget.isExpired(); n++) {
            Optional<Move> opt = session.sampleRandomLegalMove(random);
            if (opt.isEmpty()) {
                break;
            }
            Move raw = opt.get();
            if (!seen.add(stableSignature(raw))) {
                continue;
            }
            UndoRecord undo = session.applyTurn(raw);
            double score = session.evaluateForRoot(root) + HeuristicEvaluation.turnShapeBonus(raw);
            session.undoTurn(raw, undo);
            int cmp = Double.compare(score, bestScore);
            if (cmp > 0) {
                bestScore = score;
                tied.clear();
                tied.add(raw);
            } else if (cmp == 0) {
                tied.add(raw);
            }
        }

        CpuMoveSupport.restoreBaseline(game, baseline);
        if (tied.isEmpty()) {
            Game probe = Game.restoredFromMemento(baseline);
            Move fallback =
                    DefaultRuleEngine.sampleRandomLegalCompleteMove(probe, random)
                            .orElseThrow(() -> new IllegalStateException("no legal complete moves"));
            return CpuMoveSupport.engineLegalCopy(baseline, fallback, random);
        }
        Move choice = tied.get(random.nextInt(tied.size()));
        return CpuMoveSupport.engineLegalCopy(baseline, choice, random);
    }

    /* Dedup key from step endpoints and kinds so the sampler does not score the same turn shape repeatedly. */
    private static String stableSignature(Move m) {
        StringBuilder sb = new StringBuilder(m.getSteps().size() * 10);
        for (Step st : m.getSteps()) {
            sb.append(st.getFrom().toAlgebraic()).append(st.getTo().toAlgebraic());
            StepKind k = st.getKind();
            sb.append(k == null ? 'S' : k.ordinal());
        }
        return sb.toString();
    }
}
