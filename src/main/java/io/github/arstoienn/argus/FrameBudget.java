package io.github.arstoienn.argus;

/**
 * Picks the frames the second view renders on, so that it costs no more than a share of frame time.
 *
 * It keeps moving averages of the main frame time and of what one second pass costs, and renders
 * once every n frames, where n is the pass cost over the budget's share of a frame, rounded up.
 * Frames in between present the last picture again.
 */
final class FrameBudget {
    private static final long STALL_NS = 1_000_000_000L;
    private static final int MAX_GAP = 20;

    private static long lastFrameStart;
    private static long frameNs;
    private static long passNs;
    private static int framesSinceRender;

    private FrameBudget() {
    }

    static void frameStarted() {
        long now = System.nanoTime();
        long elapsed = now - lastFrameStart;
        // A gap of a second or more is a stall or a pause, not a frame time.
        if (lastFrameStart != 0L && elapsed > 0L && elapsed < STALL_NS) {
            frameNs = average(frameNs, elapsed);
        }
        lastFrameStart = now;
    }

    static void passFinished(long nanos) {
        passNs = average(passNs, nanos);
    }

    /** Counts this frame and says whether the second view renders on it. */
    static boolean renderThisFrame() {
        if (++framesSinceRender < gap()) {
            return false;
        }
        framesSinceRender = 0;
        return true;
    }

    private static int gap() {
        int percent = ArgusConfig.get().secondViewBudgetPercent;
        if (percent <= 0 || frameNs == 0L || passNs == 0L) {
            return 1;
        }
        double budgetNs = frameNs * (percent / 100.0);
        return (int) Math.max(1.0, Math.min(MAX_GAP, Math.ceil(passNs / budgetNs)));
    }

    private static long average(long average, long sample) {
        return average == 0L ? sample : (average * 7L + sample) / 8L;
    }
}
