package util;

import config.ApplicationConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class TestRunExecutor {

    private static final String MVN = ApplicationConfig.load().mavenPath();

    public static String runTests() {
        FilesUtil.clearDirectory("target/allure-results");
        return run(MVN, "-Dtest=org.demo.generated.GeneratedUiTest", "test");
    }

    public static String buildAllureReport() {
        return run(MVN, "allure:report");
    }

    private static String run(String... command) {
        try {
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.waitFor();
            return "Команда: " + String.join(" ", command)
                    + "\nКод завершения: " + exitCode
                    + "\nЛог:\n" + output;
        } catch (IOException e) {
            throw new RuntimeException("Не удалось запустить Maven", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Запуск Maven был прерван", e);
        }
    }
}
