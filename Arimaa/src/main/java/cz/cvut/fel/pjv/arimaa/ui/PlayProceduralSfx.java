package cz.cvut.fel.pjv.arimaa.ui;

import cz.cvut.fel.pjv.arimaa.model.Game;
import cz.cvut.fel.pjv.arimaa.model.enums.PlayerSide;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Short procedural move / trap cues via {@link javax.sound.sampled} (no audio files). Playback runs on a
 * background thread so the JavaFX thread is never blocked.
 */
final class PlayProceduralSfx {

    private static final Logger log = LoggerFactory.getLogger(PlayProceduralSfx.class);

    private static final int SAMPLE_RATE = 44_100;
    private static final AudioFormat FORMAT =
            new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, SAMPLE_RATE, 16, 1, 2, SAMPLE_RATE, false);

    private static final Executor ASYNC = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("arimaa-sfx-", 0L).factory());

    private PlayProceduralSfx() {}

    static int totalTrapCaptures(Game g) {
        if (g == null) {
            return 0;
        }
        return g.getTrapCapturesSnapshot(PlayerSide.GOLD).size() + g.getTrapCapturesSnapshot(PlayerSide.SILVER).size();
    }

    /**
     * Queues wood-tap (+ optional trap wail) if {@code trapsBefore} vs {@code gAfter} trap totals imply a board
     * advance was applied on {@code gAfter}.
     */
    static void playAfterBoardMutation(int trapsBefore, Game gAfter) {
        if (gAfter == null) {
            return;
        }
        int trapsAfter = totalTrapCaptures(gAfter);
        int delta = trapsAfter - trapsBefore;
        ASYNC.execute(() -> playWoodThenMaybeTrap(delta > 0));
    }

    private static void playWoodThenMaybeTrap(boolean trap) {
        try {
            short[] wood = synthesizeWoodTap();
            int gap = (int) (SAMPLE_RATE * 0.055);
            short[] trapWail = trap ? synthesizeTrapWail() : null;
            int totalSamples = wood.length + (trapWail != null ? gap + trapWail.length : 0);
            short[] combined = new short[totalSamples];
            System.arraycopy(wood, 0, combined, 0, wood.length);
            if (trapWail != null) {
                System.arraycopy(trapWail, 0, combined, wood.length + gap, trapWail.length);
            }
            byte[] pcm = shortsToLittleEndianPcm16(combined);
            drainPcm(pcm);
        } catch (LineUnavailableException | IllegalArgumentException ex) {
            log.debug("procedural sfx skipped: {}", ex.toString());
        } catch (Exception ex) {
            log.debug("procedural sfx error", ex);
        }
    }

    private static short[] synthesizeWoodTap() {
        int n = (int) (SAMPLE_RATE * 0.036);
        double tau = SAMPLE_RATE * 0.0105;
        double[] freqs = {520, 980, 1420};
        double[] weights = {1.0, 0.58, 0.36};
        double[] phases = {0, 0.7, 1.15};
        short[] out = new short[n];
        var rnd = java.util.concurrent.ThreadLocalRandom.current();
        for (int i = 0; i < n; i++) {
            double t = i / (double) SAMPLE_RATE;
            double env = Math.exp(-i / tau);
            double s = 0;
            for (int p = 0; p < freqs.length; p++) {
                s += weights[p] * Math.sin(2 * Math.PI * freqs[p] * t + phases[p]) * env;
            }
            if (i < (int) (SAMPLE_RATE * 0.0028)) {
                s += 0.22 * (rnd.nextDouble() * 2 - 1) * (1.0 - i / (SAMPLE_RATE * 0.0028));
            }
            out[i] = (short) (s * 5200);
        }
        normalizePeak(out, 9800);
        return out;
    }

    private static short[] synthesizeTrapWail() {
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
            /* Subtle shimmer only — strong tremolo read as a repeated “wail”. */
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
                var rnd = java.util.concurrent.ThreadLocalRandom.current();
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
        copyShortsIntoPcm(samples, b, 0);
        return b;
    }

    private static void copyShortsIntoPcm(short[] samples, byte[] dest, int destSampleOffset) {
        int di = destSampleOffset * 2;
        for (short v : samples) {
            dest[di++] = (byte) (v & 0xff);
            dest[di++] = (byte) ((v >> 8) & 0xff);
        }
    }

    private static void drainPcm(byte[] interleaved) throws LineUnavailableException {
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, FORMAT);
        try (SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info)) {
            line.open(FORMAT);
            line.start();
            line.write(interleaved, 0, interleaved.length);
            line.drain();
            line.stop();
        }
    }
}
