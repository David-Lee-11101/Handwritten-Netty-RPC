import java.util.Arrays;

public final class NanoTimeResolutionMain {
    private NanoTimeResolutionMain() { }

    public static void main(String[] args) {
        final int samples = 1_000_000;
        long[] positiveDeltas = new long[samples];
        int count = 0;
        long previous = System.nanoTime();
        for (int index = 0; index < samples; index++) {
            long current = System.nanoTime();
            long delta = current - previous;
            if (delta > 0) positiveDeltas[count++] = delta;
            previous = current;
        }
        if (count == 0) throw new IllegalStateException("System.nanoTime did not advance");
        positiveDeltas = Arrays.copyOf(positiveDeltas, count);
        Arrays.sort(positiveDeltas);
        System.out.println("samples=" + samples);
        System.out.println("positiveDeltas=" + count);
        System.out.println("minimumNanos=" + positiveDeltas[0]);
        System.out.println("medianNanos=" + percentile(positiveDeltas, 0.50));
        System.out.println("p99Nanos=" + percentile(positiveDeltas, 0.99));
        System.out.println("maximumNanos=" + positiveDeltas[count - 1]);
    }

    private static long percentile(long[] values, double percentile) {
        int index = (int) Math.ceil(percentile * values.length) - 1;
        return values[Math.max(0, Math.min(index, values.length - 1))];
    }
}
