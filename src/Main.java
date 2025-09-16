
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class Main {

    static class Worker implements Runnable {
        private final int jobId;
        private final AtomicLong counter;

        Worker(int jobId, AtomicLong counter) {
            this.jobId = jobId;
            this.counter = counter;
        }

        @Override
        public void run() {
            try {
                // имитация "работы"
                Thread.sleep(50);
                counter.incrementAndGet();
                // можно включить лог для дебага:
                // System.out.println("job " + jobId + " done by " + Thread.currentThread());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static Result runExperiment(int numJobs) throws InterruptedException {
        AtomicLong counter = new AtomicLong();
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        Instant start = Instant.now();
        for (int j = 0; j < numJobs; j++) {
            executor.submit(new Worker(j, counter));
        }

        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.HOURS);
        Duration elapsed = Duration.between(start, Instant.now());

        return new Result(elapsed, counter.get());
    }

    public static void main(String[] args) throws InterruptedException {
        int[][] tests = {
                {10},
                {100},
                {1000},
                {10_000},
                {100_000},
                {1000_000},
                {10_000_000},
        };

        for (int[] t : tests) {
            int jobs = t[0];
            System.out.printf("=== jobs=%d ===%n", jobs);
            Result r = runExperiment(jobs);
            System.out.printf("time: %d ms, counter=%d%n%n",
                    r.elapsed.toMillis(), r.counter);
        }
    }

    record Result(Duration elapsed, long counter) {
    }
}
