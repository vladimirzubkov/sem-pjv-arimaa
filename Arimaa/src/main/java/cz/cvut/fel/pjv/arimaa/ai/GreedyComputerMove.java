package cz.cvut.fel.pjv.arimaa.ai;

import cz.cvut.fel.pjv.arimaa.model.DefaultRuleEngine;
import cz.cvut.fel.pjv.arimaa.model.Game;
import cz.cvut.fel.pjv.arimaa.model.GameMemento;
import cz.cvut.fel.pjv.arimaa.model.Move;
import cz.cvut.fel.pjv.arimaa.model.PlayHalfTurn;
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

    public static Move chooseMove(Game game, Random random) {
        Objects.requireNonNull(game, "game");
        Objects.requireNonNull(random, "random");
        if (game.getState() != GameState.PLAY) {
            throw new IllegalStateException("PLAY only");
        }
        if (!DefaultRuleEngine.existsLegalTurn(game)) {
            throw new IllegalStateException("no legal complete moves");
        }
        PlayerSide root = game.getSideToMove();
        SearchBudget budget = new SearchBudget(SEARCH_BUDGET_MS);
        Set<String> seen = new HashSet<>();
        double bestScore = -Double.MAX_VALUE;
        List<Move> tied = new ArrayList<>();

        for (int n = 0; n < MAX_SAMPLES && !budget.isExpired(); n++) {
            Optional<Move> opt = DefaultRuleEngine.sampleRandomLegalCompleteMove(game, random);
            if (opt.isEmpty()) {
                break;
            }
            Move raw = opt.get();
            if (!seen.add(stableSignature(raw))) {
                continue;
            }
            Move trial = PlayHalfTurn.copyMove(raw);
            GameMemento snap = game.createMemento();
            try {
                game.applyMove(trial);
                double score =
                        HeuristicEvaluation.evaluateForRoot(game, root) + HeuristicEvaluation.turnShapeBonus(raw);
                int cmp = Double.compare(score, bestScore);
                if (cmp > 0) {
                    bestScore = score;
                    tied.clear();
                    tied.add(raw);
                } else if (cmp == 0) {
                    tied.add(raw);
                }
            } finally {
                game.restoreMemento(snap);
            }
        }

        if (tied.isEmpty()) {
            Move fallback =
                    DefaultRuleEngine.sampleRandomLegalCompleteMove(game, random)
                            .orElseThrow(() -> new IllegalStateException("no legal complete moves"));
            return PlayHalfTurn.copyMove(fallback);
        }
        Move choice = tied.get(random.nextInt(tied.size()));
        return PlayHalfTurn.copyMove(choice);
    }

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
