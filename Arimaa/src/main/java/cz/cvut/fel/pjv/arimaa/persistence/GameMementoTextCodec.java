package cz.cvut.fel.pjv.arimaa.persistence;

import cz.cvut.fel.pjv.arimaa.model.GameMemento;
import cz.cvut.fel.pjv.arimaa.model.PieceType;
import cz.cvut.fel.pjv.arimaa.model.PlayerSide;
import cz.cvut.fel.pjv.arimaa.util.BoardConstants;

import java.util.ArrayList;
import java.util.List;

/**
 * Line-oriented textual snapshot of {@link GameMemento} (board grid, reserves, setup hand, captures).
 * <p>
 * Grid rows use {@code R1}…{@code R8} labels; fully empty rows are omitted when encoding. Decoding fills only
 * rows present in the file; missing ranks stay empty.
 */
public final class GameMementoTextCodec {

    public static final String MAGIC = "ArimaaTxtSave 1";

    private GameMementoTextCodec() {
    }

    /** Serializes a memento into editable lines (MAGIC, meta, hand, non-empty {@code R#} rows, reserves, captures). */
    public static List<String> encode(GameMemento m) {
        ArrayList<String> lines = new ArrayList<>();
        lines.add(MAGIC);
        lines.add(metaLine(m));
        lines.add(handLine(m.setupHand()));
        GameMemento.CellSnap[][] grid = m.grid();
        for (int r = 0; r < BoardConstants.BOARD_SIZE; r++) {
            boolean allEmpty = true;
            for (int f = 0; f < BoardConstants.BOARD_SIZE; f++) {
                if (grid[r][f] != null) {
                    allEmpty = false;
                    break;
                }
            }
            if (allEmpty) {
                continue;
            }
            StringBuilder sb = new StringBuilder(BoardConstants.BOARD_SIZE);
            for (int f = 0; f < BoardConstants.BOARD_SIZE; f++) {
                GameMemento.CellSnap c = grid[r][f];
                sb.append(c == null ? '.' : encodeCell(c));
            }
            lines.add("R" + (r + 1) + " " + sb);
        }
        lines.add("RESERVE_GOLD " + encodeReserveLetters(m.goldReserve()));
        lines.add("RESERVE_SILVER " + encodeReserveLetters(m.silverReserve()));
        lines.add("CAPTURES_GOLD " + encodeCaptureTypes(m.trapCapturesByGold()));
        lines.add("CAPTURES_SILVER " + encodeCaptureTypes(m.trapCapturesBySilver()));
        return lines;
    }

    /** Parses lines produced by {@link #encode(GameMemento)} back into a {@link GameMemento}. */
    public static GameMemento decode(List<String> lines) {
        if (lines.isEmpty() || !MAGIC.equals(lines.getFirst().trim())) {
            throw new IllegalArgumentException("missing or wrong magic line");
        }
        int i = 1;
        Meta meta = parseMeta(lines.get(i++));
        GameMemento.CellSnap hand = parseHandLine(lines.get(i++));
        GameMemento.CellSnap[][] grid = new GameMemento.CellSnap[BoardConstants.BOARD_SIZE][BoardConstants.BOARD_SIZE];
        if (i >= lines.size()) {
            throw new IllegalArgumentException("truncated after HAND");
        }
        while (i < lines.size()) {
            String t = lines.get(i).trim();
            if (!isSparseGridRowLine(t)) {
                break;
            }
            int rowIdx = parseRowLabelIndex(t);
            fillRowFromLine(grid, rowIdx, lines.get(i++));
        }
        List<GameMemento.CellSnap> goldR = decodeReserveLine(lines.get(i++));
        List<GameMemento.CellSnap> silverR = decodeReserveLine(lines.get(i++));
        List<PieceType> capG = decodeCapturesLine(lines.get(i++));
        List<PieceType> capS = decodeCapturesLine(lines.get(i++));

        return new GameMemento(
                meta.state,
                meta.side,
                meta.winner,
                meta.mirror,
                grid,
                goldR,
                silverR,
                hand,
                capG,
                capS);
    }

    /** {@code R1} … {@code R8} with whitespace after rank digit; excludes {@code RESERVE_*} lines. */
    private static boolean isSparseGridRowLine(String trimmed) {
        if (trimmed.length() < 4 || trimmed.charAt(0) != 'R') {
            return false;
        }
        char d = trimmed.charAt(1);
        if (d < '1' || d > '8') {
            return false;
        }
        if (trimmed.length() > 3 && Character.isDigit(trimmed.charAt(2))) {
            return false;
        }
        return Character.isWhitespace(trimmed.charAt(2));
    }

    private static int parseRowLabelIndex(String trimmed) {
        char d = trimmed.charAt(1);
        int n = d - '0';
        if (n < 1 || n > BoardConstants.BOARD_SIZE) {
            throw new IllegalArgumentException("bad row label: " + trimmed);
        }
        return n - 1;
    }

    private static void fillRowFromLine(GameMemento.CellSnap[][] grid, int rowIndex, String rowLine) {
        String trimmed = rowLine.trim();
        int space = trimmed.indexOf(' ');
        if (space < 0) {
            throw new IllegalArgumentException("bad grid row: " + rowLine);
        }
        String payload = trimmed.substring(space + 1).trim();
        if (payload.length() != BoardConstants.BOARD_SIZE) {
            throw new IllegalArgumentException("grid row width: " + payload);
        }
        for (int f = 0; f < BoardConstants.BOARD_SIZE; f++) {
            grid[rowIndex][f] = decodeCell(payload.charAt(f));
        }
    }

    private record Meta(
            cz.cvut.fel.pjv.arimaa.model.GameState state,
            PlayerSide side,
            PlayerSide winner,
            boolean mirror) {
    }

    private static Meta parseMeta(String line) {
        String[] p = line.trim().split("\\s+");
        if (p.length < 5 || !"META".equals(p[0])) {
            throw new IllegalArgumentException("META line: " + line);
        }
        return new Meta(
                cz.cvut.fel.pjv.arimaa.model.GameState.valueOf(p[1]),
                PlayerSide.valueOf(p[2]),
                "-".equals(p[4]) ? null : PlayerSide.valueOf(p[4]),
                "1".equals(p[3]));
    }

    private static String metaLine(GameMemento m) {
        String win = m.matchWinner() == null ? "-" : m.matchWinner().name();
        return "META "
                + m.state().name()
                + " "
                + m.sideToMove().name()
                + " "
                + (m.ranksMirroredForHomeCheck() ? "1" : "0")
                + " "
                + win;
    }

    private static String handLine(GameMemento.CellSnap hand) {
        if (hand == null) {
            return "HAND -";
        }
        char sideTag = hand.side() == PlayerSide.GOLD ? 'g' : 's';
        return "HAND " + encodeCell(hand) + sideTag;
    }

    private static GameMemento.CellSnap parseHandLine(String line) {
        String[] p = line.trim().split("\\s+");
        if (p.length < 2 || !"HAND".equals(p[0])) {
            throw new IllegalArgumentException("HAND line: " + line);
        }
        if ("-".equals(p[1])) {
            return null;
        }
        String tok = p[1];
        char ch = tok.charAt(0);
        PlayerSide side = tok.length() > 1 ? sideFromGs(tok.charAt(1)) : sideFromCase(ch);
        return new GameMemento.CellSnap(decodePieceLetter(ch), side);
    }

    private static PlayerSide sideFromGs(char c) {
        return switch (c) {
            case 'g' -> PlayerSide.GOLD;
            case 's' -> PlayerSide.SILVER;
            default -> throw new IllegalArgumentException("hand side tag");
        };
    }

    private static PlayerSide sideFromCase(char letter) {
        return Character.isUpperCase(letter) ? PlayerSide.GOLD : PlayerSide.SILVER;
    }

    private static char encodeCell(GameMemento.CellSnap c) {
        char letter =
                switch (c.type()) {
                    case ELEPHANT -> 'E';
                    case CAMEL -> 'M';
                    case HORSE -> 'H';
                    case DOG -> 'D';
                    case CAT -> 'C';
                    case RABBIT -> 'R';
                };
        return c.side() == PlayerSide.GOLD ? letter : Character.toLowerCase(letter);
    }

    private static GameMemento.CellSnap decodeCell(char ch) {
        if (ch == '.') {
            return null;
        }
        PlayerSide side = Character.isUpperCase(ch) ? PlayerSide.GOLD : PlayerSide.SILVER;
        return new GameMemento.CellSnap(decodePieceLetter(ch), side);
    }

    private static PieceType decodePieceLetter(char ch) {
        return switch (Character.toUpperCase(ch)) {
            case 'E' -> PieceType.ELEPHANT;
            case 'M' -> PieceType.CAMEL;
            case 'H' -> PieceType.HORSE;
            case 'D' -> PieceType.DOG;
            case 'C' -> PieceType.CAT;
            case 'R' -> PieceType.RABBIT;
            default -> throw new IllegalArgumentException("piece letter: " + ch);
        };
    }

    private static String encodeReserveLetters(List<GameMemento.CellSnap> reserve) {
        StringBuilder sb = new StringBuilder();
        for (GameMemento.CellSnap c : reserve) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(encodeCell(c));
        }
        return sb.toString();
    }

    private static List<GameMemento.CellSnap> decodeReserveLine(String line) {
        String trimmed = line.trim();
        int sp = trimmed.indexOf(' ');
        String kind = sp < 0 ? trimmed : trimmed.substring(0, sp);
        String rest = sp >= 0 ? trimmed.substring(sp + 1).trim() : "";
        if (!kind.startsWith("RESERVE_")) {
            throw new IllegalArgumentException(line);
        }
        PlayerSide side = kind.endsWith("GOLD") ? PlayerSide.GOLD : PlayerSide.SILVER;
        ArrayList<GameMemento.CellSnap> out = new ArrayList<>();
        if (rest.isEmpty()) {
            return out;
        }
        for (String tok : rest.split("\\s+")) {
            if (tok.isEmpty()) {
                continue;
            }
            char ch = tok.charAt(0);
            out.add(new GameMemento.CellSnap(decodePieceLetter(ch), side));
        }
        return out;
    }

    private static String encodeCaptureTypes(List<PieceType> types) {
        if (types.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (PieceType t : types) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(switch (t) {
                case ELEPHANT -> 'e';
                case CAMEL -> 'm';
                case HORSE -> 'h';
                case DOG -> 'd';
                case CAT -> 'c';
                case RABBIT -> 'r';
            });
        }
        return sb.toString();
    }

    private static List<PieceType> decodeCapturesLine(String line) {
        String trimmed = line.trim();
        int sp = trimmed.indexOf(' ');
        String prefix = sp < 0 ? trimmed : trimmed.substring(0, sp);
        String payload = sp >= 0 ? trimmed.substring(sp + 1).trim() : "";
        if (!prefix.startsWith("CAPTURES_")) {
            throw new IllegalArgumentException(line);
        }
        ArrayList<PieceType> out = new ArrayList<>();
        if (payload.isEmpty()) {
            return out;
        }
        for (String tok : payload.split("\\s+")) {
            if (tok.isEmpty()) {
                continue;
            }
            out.add(decodePieceLetter(tok.charAt(0)));
        }
        return out;
    }
}
