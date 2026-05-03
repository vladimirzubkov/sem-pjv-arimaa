package cz.cvut.fel.pjv.arimaa.persistence;

import cz.cvut.fel.pjv.arimaa.model.DefaultRuleEngine;
import cz.cvut.fel.pjv.arimaa.model.Game;
import cz.cvut.fel.pjv.arimaa.model.Move;
import cz.cvut.fel.pjv.arimaa.model.Piece;
import cz.cvut.fel.pjv.arimaa.model.Position;
import cz.cvut.fel.pjv.arimaa.model.Step;

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

    static boolean isTrapRemovalNotation(String token) {
        return token != null && TRAP_REMOVAL_TOKEN.matcher(token).matches();
    }

    /**
     * Counts {@code file} tokens consumed when aligning from the start: engine stream may contain extra trap-removal
     * tokens ({@code …x}) that are absent from {@code file}.
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
            break;
        }
        return fi;
    }

    /**
     * Parsed move plus whether the line contained an early-pass suffix ({@code ... pass}).
     */
    public record ParsedLine(Move move, boolean hasEarlyPassSuffix) {
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
        Move move = parsePlayBody(game, tokens);
        return new ParsedLine(move, earlyPass);
    }

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
     * Reconstructs {@link Move#getSteps()} token-by-token so engine notation matches the saved line (used when loading
     * {@link GameSerializer} files).
     */
    public static Move parsePlayBody(Game game, List<String> tokens) {
        Move move = new Move();
        if (tokens.isEmpty()) {
            return move;
        }
        int matched = 0;
        while (matched < tokens.size()) {
            Map<Position, Piece> occ = DefaultRuleEngine.simulatePlayPrefix(game, move);
            boolean progressed = false;
            Move bestTrial = null;
            int bestConsumed = matched;
            for (List<Step> bundle : DefaultRuleEngine.enumerateStepBundles(occ, game.getSideToMove())) {
                Move trial = copyMove(move);
                for (Step st : bundle) {
                    trial.getSteps().add(copyStep(st));
                }
                String body = DefaultRuleEngine.buildArimaaNotationBody(game.getBoard(), trial);
                List<String> expect = tokenize(body);
                if (expect.isEmpty()) {
                    continue;
                }
                int consumed = consumedFileTokensAllowEngineTraps(expect, tokens);
                if (consumed > bestConsumed) {
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
                        matched < tokens.size()
                                ? tokens.get(matched)
                                : "?";
                throw new IllegalArgumentException(
                        "Notaci nelze po přehrání předchozích tahů sladit s pozicí (token "
                                + matched
                                + "/"
                                + tokens.size()
                                + ": „"
                                + tail
                                + "“). Zkontrolujte shodu uloženého setupu s tahy nebo uložte partii znovu z aplikace.");
            }
        }
        return move;
    }

    private static Move copyMove(Move src) {
        Move m = new Move();
        for (Step s : src.getSteps()) {
            m.getSteps().add(copyStep(s));
        }
        return m;
    }

    private static Step copyStep(Step s) {
        Step t = new Step();
        t.setFrom(s.getFrom());
        t.setTo(s.getTo());
        t.setKind(s.getKind());
        return t;
    }
}
