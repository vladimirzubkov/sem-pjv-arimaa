package cz.cvut.fel.pjv.arimaa.persistence;

import cz.cvut.fel.pjv.arimaa.model.DefaultRuleEngine;
import cz.cvut.fel.pjv.arimaa.model.Game;
import cz.cvut.fel.pjv.arimaa.model.Move;
import cz.cvut.fel.pjv.arimaa.model.Piece;
import cz.cvut.fel.pjv.arimaa.model.Position;
import cz.cvut.fel.pjv.arimaa.model.Step;
import cz.cvut.fel.pjv.arimaa.util.ArimaaNotation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses one full-line Arimaa notation segment (prefix + body) into a {@link Move}, using the same
 * token stream as {@link DefaultRuleEngine#buildArimaaNotationBody}.
 */
public final class PlayNotationParser {

    private static final Pattern PREFIX_HEAD = Pattern.compile("^\\s*(\\d+)([gs])\\s*(.*)$");
    /** Trap victim token from {@link DefaultRuleEngine#appendTrapNotation}: {@code Rc3x}. */
    private static final Pattern TRAP_REMOVAL_TOKEN = Pattern.compile("^[EMDHCRemdhcr][a-h][1-8]x$");

    private PlayNotationParser() {
    }

    /** True for trap-capture tokens like {@code Rc3x} (not real steps; stripped when parsing). */
    static boolean isTrapRemovalNotation(String token) {
        return token != null && TRAP_REMOVAL_TOKEN.matcher(token).matches();
    }

    /**
     * Counts {@code file} tokens consumed when aligning from the start: trap-removal tokens ({@code …x}) may appear
     * only in the engine stream, only in the saved file, or in both.
     */
    static int consumedFileTokensAllowEngineTraps(List<String> engine, List<String> file) {
        int ei = 0;
        int fi = 0;
        while (ei < engine.size() && fi < file.size()) {
            String e = engine.get(ei);
            String f = file.get(fi);
            if (e.equals(f)) {
                ei++;
                fi++;
                continue;
            }
            if (isTrapRemovalNotation(e)) {
                ei++;
                continue;
            }
            if (isTrapRemovalNotation(f)) {
                fi++;
                continue;
            }
            break;
        }
        return fi;
    }

    /**
     * Parsed move plus trailing markers: {@code ... pass} (committed short turn) and {@code ... draft}
     * (uncommitted 4-step prefix — player has not pressed End turn yet).
     */
    public record ParsedLine(Move move, boolean hasEarlyPassSuffix, boolean hasUncommittedDraftSuffix) {
        public ParsedLine(Move move, boolean hasEarlyPassSuffix) {
            this(move, hasEarlyPassSuffix, false);
        }
    }

    /**
     * Parses a single notation line against {@code game}'s current board (start-of-turn position).
     */
    public static ParsedLine parseLine(Game game, String line) {
        Matcher m = PREFIX_HEAD.matcher(line.trim());
        if (!m.matches()) {
            throw new IllegalArgumentException("notation line needs prefix like '1g ': " + line);
        }
        String body = m.group(3).trim();
        ArrayList<String> tokens = tokenize(body);
        boolean earlyPass = stripEarlyPassSuffix(tokens);
        boolean uncommittedDraft = stripUncommittedDraftSuffix(tokens);
        Move move = parsePlayBody(game, tokens);
        return new ParsedLine(move, earlyPass, uncommittedDraft);
    }

    /** Splits notation body on whitespace into move/trap/pass tokens. */
    static ArrayList<String> tokenize(String body) {
        if (body.isEmpty()) {
            return new ArrayList<>();
        }
        String[] parts = body.split("\\s+");
        ArrayList<String> out = new ArrayList<>(parts.length);
        for (String p : parts) {
            if (!p.isEmpty()) {
                out.add(p);
            }
        }
        return out;
    }

    /**
     * Removes trailing {@code ... pass} marker tokens when present.
     *
     * @return {@code true} if those markers were removed
     */
    static boolean stripEarlyPassSuffix(ArrayList<String> tokens) {
        int n = tokens.size();
        if (n >= 2 && "...".equals(tokens.get(n - 2)) && "pass".equals(tokens.get(n - 1))) {
            tokens.remove(n - 1);
            tokens.remove(n - 2);
            return true;
        }
        return false;
    }

    /**
     * Removes trailing {@link ArimaaNotation#UNCOMMITTED_DRAFT_MARKER} ({@code ... draft}) when present.
     *
     * @return {@code true} if those markers were removed
     */
    static boolean stripUncommittedDraftSuffix(ArrayList<String> tokens) {
        int n = tokens.size();
        if (n >= 2 && "...".equals(tokens.get(n - 2)) && "draft".equals(tokens.get(n - 1))) {
            tokens.remove(n - 1);
            tokens.remove(n - 2);
            return true;
        }
        return false;
    }

    /**
     * Reconstructs {@link Move#getSteps()} from move tokens only. Tokens such as {@code Rc3x} (trap removal) are
     * recorded in saved notation for humans but are not Arimaa steps — they are stripped before matching.
     */
    public static Move parsePlayBody(Game game, List<String> tokens) {
        ArrayList<String> moveTokens = new ArrayList<>();
        for (String t : tokens) {
            if (!isTrapRemovalNotation(t)) {
                moveTokens.add(t);
            }
        }
        Move move = new Move();
        if (moveTokens.isEmpty()) {
            return move;
        }
        int matched = 0;
        while (matched < moveTokens.size()) {
            Map<Position, Piece> occ = DefaultRuleEngine.simulatePlayPrefix(game, move);
            boolean progressed = false;
            Move bestTrial = null;
            int bestConsumed = matched;
            for (List<Step> bundle : DefaultRuleEngine.enumerateStepBundles(occ, game.getSideToMove())) {
                Move trial = copyMove(move);
                for (Step st : bundle) {
                    trial.getSteps().add(copyStep(st));
                }
                if (trial.getSteps().size() > 4) {
                    continue;
                }
                String body = DefaultRuleEngine.buildArimaaNotationBody(game.getBoard(), trial);
                List<String> expect = tokenize(body);
                if (expect.isEmpty()) {
                    continue;
                }
                int consumed = consumedFileTokensAllowEngineTraps(expect, moveTokens);
                int trialSteps = trial.getSteps().size();
                int bestSteps = bestTrial == null ? 0 : bestTrial.getSteps().size();
                if (consumed > bestConsumed
                        || (consumed == bestConsumed && trialSteps > bestSteps)) {
                    bestConsumed = consumed;
                    bestTrial = trial;
                }
            }
            if (bestTrial != null) {
                move = bestTrial;
                matched = bestConsumed;
                progressed = true;
            }
            if (!progressed) {
                String tail =
                        matched < moveTokens.size()
                                ? moveTokens.get(matched)
                                : "?";
                throw new IllegalArgumentException(
                        "Notaci nelze po přehrání předchozích tahů sladit s pozicí (token "
                                + matched
                                + "/"
                                + moveTokens.size()
                                + ": „"
                                + tail
                                + "“). Zkontrolujte shodu uloženého setupu s tahy nebo uložte partii znovu z aplikace.");
            }
        }
        return move;
    }

    /* Deep copy of accumulated steps while aligning saved tokens to legal bundles. */
    private static Move copyMove(Move src) {
        Move m = new Move();
        for (Step s : src.getSteps()) {
            m.getSteps().add(copyStep(s));
        }
        return m;
    }

    /* Step copy for parser trials (positions + kind). */
    private static Step copyStep(Step s) {
        Step t = new Step();
        t.setFrom(s.getFrom());
        t.setTo(s.getTo());
        t.setKind(s.getKind());
        return t;
    }
}
