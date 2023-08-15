package main.app;

import java.util.concurrent.TimeUnit;

public class Stopwatch {
    private long startTime;

    public void start() {
        startTime = System.nanoTime();
    }
    public void printElapsedTime() {
            long endTime = System.nanoTime();
            long elapsedTime = endTime - startTime;

            long minutes = TimeUnit.NANOSECONDS.toMinutes(elapsedTime);
            long seconds = TimeUnit.NANOSECONDS.toSeconds(elapsedTime) - TimeUnit.MINUTES.toSeconds(minutes);

            // Convert the remaining nanoseconds to a fraction of a second, rounded to two decimal places
            long remainingNanos = elapsedTime - TimeUnit.SECONDS.toNanos(seconds) - TimeUnit.MINUTES.toNanos(minutes);
            double fractionOfSecond = (double) remainingNanos / 1_000_000_000.0;
            double secondsWithFraction = seconds + fractionOfSecond;

            // Format the seconds with two decimal places
            String formattedSeconds = String.format("%.2f", secondsWithFraction);

            System.out.println("Elapsed Time: " + minutes + " minutes " + formattedSeconds + " seconds");
        }
    public void printElapsedTime(String msg) {
        long endTime = System.nanoTime();
        long elapsedTime = endTime - startTime;

        long minutes = TimeUnit.NANOSECONDS.toMinutes(elapsedTime);
        long seconds = TimeUnit.NANOSECONDS.toSeconds(elapsedTime) - TimeUnit.MINUTES.toSeconds(minutes);

        // Convert the remaining nanoseconds to a fraction of a second, rounded to two decimal places
        long remainingNanos = elapsedTime - TimeUnit.SECONDS.toNanos(seconds) - TimeUnit.MINUTES.toNanos(minutes);
        double fractionOfSecond = (double) remainingNanos / 1_000_000_000.0;
        double secondsWithFraction = seconds + fractionOfSecond;

        // Format the seconds with two decimal places
        String formattedSeconds = String.format("%.2f", secondsWithFraction);
        System.out.println(msg);
        System.out.println("Elapsed Time: " + minutes + " minutes " + formattedSeconds + " seconds");
    }
}

