import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class NoContention {

    private static Integer doWork(int jobId) {
        try {
            // имитация работы
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return 1; // каждая задача вернёт "единичку"
    }

    private static Result runExperiment(int numJobs) throws InterruptedException, ExecutionException {
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        List<Future<Integer>> futures = new ArrayList<>(numJobs);

        Instant start = Instant.now();
        for (int j = 0; j < numJobs; j++) {
            final int jobId = j;
            futures.add(executor.submit(() -> doWork(jobId)));
        }

        long counter = 0;
        for (Future<Integer> f : futures) {
            counter += f.get(); // собираем результаты
        }

        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.HOURS);
        Duration elapsed = Duration.between(start, Instant.now());

        return new Result(elapsed, counter);
    }

    public static void main(String[] args) throws Exception {
        int[] tests = {10, 100, 1000, 10_000, 100_000, 1_000_000, 10_000_000};

        for (int jobs : tests) {
            System.out.printf("=== jobs=%d ===%n", jobs);
            Result r = runExperiment(jobs);
            System.out.printf("time: %d ms, counter=%d%n%n",
                    r.elapsed.toMillis(), r.counter);
        }
    }

    record Result(Duration elapsed, long counter) {}
}
