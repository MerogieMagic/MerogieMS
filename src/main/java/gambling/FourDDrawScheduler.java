package gambling;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Automatically triggers the daily 4D draw and evaluation.
 * Safe on restart; checks for existing draw before generating.
 */
public class FourDDrawScheduler {

    private static final LocalTime DRAW_TIME = LocalTime.of(18, 0); // 6 PM
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public static void init() {
        scheduleNextDraw();
    }

    private static void scheduleNextDraw() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextRun = LocalDate.now().atTime(DRAW_TIME);

        if (now.isAfter(nextRun)) {
            nextRun = nextRun.plusDays(1); // Schedule next day
        }

        long delay = Duration.between(now, nextRun).toMillis();

        scheduler.schedule(() -> {
            LocalDate today = LocalDate.now();
            if (!FourDResultManager.hasDrawToday(today)) {
                String first = random4D();
                String second = random4D();
                String third = random4D();
                List<String> starters = generateRandomSet(10);
                List<String> consolations = generateRandomSet(10);

                FourDResultManager.storeDraw(today, first, second, third, starters, consolations);
                FourDResultManager.evaluateBets(today);
                System.out.println("[4D Scheduler] Draw completed for " + today);
            } else {
                System.out.println("[4D Scheduler] Draw already exists for " + today);
            }

            // Reschedule
            scheduleNextDraw();
        }, delay, TimeUnit.MILLISECONDS);
    }

    private static String random4D() {
        return String.format("%04d", new Random().nextInt(10000));
    }

    private static List<String> generateRandomSet(int count) {
        Set<String> numbers = new LinkedHashSet<>();
        while (numbers.size() < count) {
            numbers.add(random4D());
        }
        return new ArrayList<>(numbers);
    }
}
