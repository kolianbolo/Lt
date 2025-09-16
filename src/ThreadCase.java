

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class ThreadCase {

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
                Thread.sleep(50); // имитация работы
                counter.incrementAndGet();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static Result runExperiment(int numJobs, ExecutorService executor) throws InterruptedException {
        AtomicLong counter = new AtomicLong();
        Instant start = Instant.now();

        for (int j = 0; j < numJobs; j++) {
            executor.submit(new Worker(j, counter));
        }

        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.HOURS);

        Duration elapsed = Duration.between(start, Instant.now());
        return new Result(elapsed, counter.get());
    }

    public static void main(String[] args) throws Exception {
        int[] tests = {10, 100, 1000, 10_000, 100_000, 10_000_000};

        System.out.println("=== Virtual Threads ===");
        for (int jobs : tests) {
            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                Result r = runExperiment(jobs, executor);
                System.out.printf("jobs=%d time=%d ms counter=%d%n", jobs, r.elapsed.toMillis(), r.counter);
            }
        }

        System.out.println("\n=== Classic FixedThreadPool ===");
        for (int jobs : tests) {
            try (ExecutorService executor = Executors.newFixedThreadPool(10000)) { // фиксированный пул из 100 тредов
                Result r = runExperiment(jobs, executor);
                System.out.printf("jobs=%d time=%d ms counter=%d%n", jobs, r.elapsed.toMillis(), r.counter);
            }
        }
    }

    record Result(Duration elapsed, long counter) {
    }
}
