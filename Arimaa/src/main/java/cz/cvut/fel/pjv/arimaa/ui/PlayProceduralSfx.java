package cz.cvut.fel.pjv.arimaa.ui;

import cz.cvut.fel.pjv.arimaa.model.Game;
import cz.cvut.fel.pjv.arimaa.model.enums.PlayerSide;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import java.util.Random;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Short procedural move / trap cues via {@link javax.sound.sampled} (no audio files). PCM is synthesized once at
 * class load; playback uses a single {@link SourceDataLine} on a dedicated thread (re-opened only if the line
 * dies) so Windows does not drop every other cue from open/close races.
 */
final class PlayProceduralSfx {

    private static final Logger log = LoggerFactory.getLogger(PlayProceduralSfx.class);

    private static final int SAMPLE_RATE = 44_100;
    private static final int TRAP_GAP_SAMPLES = (int) (SAMPLE_RATE * 0.055);
    private static final AudioFormat FORMAT =
            new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, SAMPLE_RATE, 16, 1, 2, SAMPLE_RATE, false);

    private static final byte[] PCM_MOVE_TAP;
    private static final byte[] PCM_MOVE_WITH_TRAP;
    private static final byte[] PCM_TRAP_ONLY;

    static {
        Random rnd = new Random(0x4152494DL);
        short[] wood = synthesizeWoodTap(rnd);
        short[] trap = synthesizeTrapWail(rnd);
        PCM_MOVE_TAP = shortsToLittleEndianPcm16(wood);
        short[] woodTrap = new short[wood.length + TRAP_GAP_SAMPLES + trap.length];
        System.arraycopy(wood, 0, woodTrap, 0, wood.length);
        System.arraycopy(trap, 0, woodTrap, wood.length + TRAP_GAP_SAMPLES, trap.length);
        PCM_MOVE_WITH_TRAP = shortsToLittleEndianPcm16(woodTrap);
        short[] trapOnly = new short[TRAP_GAP_SAMPLES + trap.length];
        System.arraycopy(trap, 0, trapOnly, TRAP_GAP_SAMPLES, trap.length);
        PCM_TRAP_ONLY = shortsToLittleEndianPcm16(trapOnly);
    }

    private static final Executor SFX_EXECUTOR =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "arimaa-sfx");
                t.setDaemon(true);
                return t;
            });

    private static final Object LINE_LOCK = new Object();
    private static SourceDataLine sharedLine;

    private PlayProceduralSfx() {}

    static int totalTrapCaptures(Game g) {
        if (g == null) {
            return 0;
        }
        return g.getTrapCapturesSnapshot(PlayerSide.GOLD).size() + g.getTrapCapturesSnapshot(PlayerSide.SILVER).size();
    }

    /**
     * Queues {@code woodCues} wood taps and, if trap totals increased, one trap wail per new capture.
     */
    static void playAfterBoardMutation(int trapsBefore, Game gAfter, int woodCues) {
        if (gAfter == null || woodCues <= 0) {
            return;
        }
        int trapsAfter = totalTrapCaptures(gAfter);
        int trapWails = Math.max(0, trapsAfter - trapsBefore);
        final int woods = woodCues;
        SFX_EXECUTOR.execute(() -> playSequence(woods, trapWails));
    }

    private static void playSequence(int woodCount, int trapWailCount) {
        try {
            synchronized (LINE_LOCK) {
                int trapsLeft = trapWailCount;
                for (int i = 0; i < woodCount; i++) {
                    boolean withTrap = trapsLeft > 0 && i == woodCount - 1;
                    if (withTrap) {
                        trapsLeft--;
                    }
                    playCached(withTrap ? PCM_MOVE_WITH_TRAP : PCM_MOVE_TAP);
                }
                while (trapsLeft-- > 0) {
                    playCached(PCM_TRAP_ONLY);
                }
            }
        } catch (LineUnavailableException | IllegalArgumentException ex) {
            log.debug("procedural sfx skipped: {}", ex.toString());
        } catch (Exception ex) {
            log.debug("procedural sfx error", ex);
        }
    }

    private static void playCached(byte[] pcm) throws LineUnavailableException {
        SourceDataLine line = ensureOpenLine();
        line.start();
        line.write(pcm, 0, pcm.length);
        line.drain();
        line.flush();
    }

    private static SourceDataLine ensureOpenLine() throws LineUnavailableException {
        if (sharedLine != null && sharedLine.isOpen()) {
            return sharedLine;
        }
        closeSharedLineQuietly();
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, FORMAT);
        SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);
        line.open(FORMAT);
        sharedLine = line;
        return line;
    }

    private static void closeSharedLineQuietly() {
        if (sharedLine == null) {
            return;
        }
        try {
            if (sharedLine.isOpen()) {
                sharedLine.stop();
                sharedLine.close();
            }
        } catch (Exception ignored) {
            // best effort
        }
        sharedLine = null;
    }

    private static short[] synthesizeWoodTap(Random rnd) {
        int n = (int) (SAMPLE_RATE * 0.036);
        double tau = SAMPLE_RATE * 0.0105;
        double[] freqs = {520, 980, 1420};
        double[] weights = {1.0, 0.58, 0.36};
        double[] phases = {0, 0.7, 1.15};
        short[] out = new short[n];
        int attackSamples = (int) (SAMPLE_RATE * 0.0028);
        for (int i = 0; i < n; i++) {
            double t = i / (double) SAMPLE_RATE;
            double env = Math.exp(-i / tau);
            double s = 0;
            for (int p = 0; p < freqs.length; p++) {
                s += weights[p] * Math.sin(2 * Math.PI * freqs[p] * t + phases[p]) * env;
            }
            if (i < attackSamples) {
                s += 0.22 * (rnd.nextDouble() * 2 - 1) * (1.0 - i / (double) attackSamples);
            }
            out[i] = (short) (s * 5200);
        }
        normalizePeak(out, 9800);
        return out;
    }

    private static short[] synthesizeTrapWail(Random rnd) {
        int n = (int) (SAMPLE_RATE * 0.46);
        double fHi = 640;
        double fLo = 118;
        short[] out = new short[n];
        double phase = 0;
        for (int i = 0; i < n; i++) {
            double prog = i / (double) (n - 1);
            double progCurve = prog * prog;
            double f = fHi * Math.pow(fLo / fHi, progCurve);
            phase += 2 * Math.PI * f / SAMPLE_RATE;
            double s = Math.sin(phase);
            double trem = 1.0;
            if (prog < 0.28) {
                trem = 0.92 + 0.08 * Math.sin(2 * Math.PI * 4.0 * (i / (double) SAMPLE_RATE));
            }
            double env = trem;
            if (prog > 0.68) {
                double tail = (prog - 0.68) / 0.32;
                env *= Math.cos(tail * Math.PI / 2);
            }
            s *= env;
            if (prog > 0.78) {
                s += 0.12 * (rnd.nextDouble() * 2 - 1) * (prog - 0.78) / 0.22;
            }
            out[i] = (short) (s * 9800);
        }
        normalizePeak(out, 12000);
        return out;
    }

    private static void normalizePeak(short[] samples, int targetPeak) {
        int max = 1;
        for (short s : samples) {
            int a = Math.abs(s);
            if (a > max) {
                max = a;
            }
        }
        double scale = targetPeak / (double) max;
        for (int i = 0; i < samples.length; i++) {
            int v = (int) Math.round(samples[i] * scale);
            if (v > Short.MAX_VALUE) {
                v = Short.MAX_VALUE;
            } else if (v < Short.MIN_VALUE) {
                v = Short.MIN_VALUE;
            }
            samples[i] = (short) v;
        }
    }

    private static byte[] shortsToLittleEndianPcm16(short[] samples) {
        byte[] b = new byte[samples.length * 2];
        int di = 0;
        for (short v : samples) {
            b[di++] = (byte) (v & 0xff);
            b[di++] = (byte) ((v >> 8) & 0xff);
        }
        return b;
    }
}
