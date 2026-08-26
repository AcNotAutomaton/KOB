package com.kob.botrunningsystem.service.impl.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 在独立子进程中编译并执行用户 Bot 代码。
 *
 * 隔离措施：
 *  - 用户代码在单独的 JVM 子进程中运行，System.exit / 死循环只影响子进程；
 *  - 子进程工作目录是每次执行独立的临时目录，input.txt 不会互相覆盖；
 *  - 子进程环境变量被清空，拿不到服务进程的数据库凭据等敏感信息；
 *  - 超时后 destroyForcibly 强杀子进程；
 *  - 用户代码的编译禁用注解处理器（-proc:none），避免编译期执行任意代码。
 */
public class Consumer {
    private static final Logger log = LoggerFactory.getLogger(Consumer.class);

    private static final long BOT_TIMEOUT_MS = 2000;  // 与旧实现一致的 2 秒执行超时
    private static final String receiveBotMoveUrl = "http://127.0.0.1:3000/pk/receive/bot/move/";

    private static final Pattern PACKAGE_PATTERN = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);
    private static final Pattern CLASS_PATTERN = Pattern.compile("public\\s+class\\s+(\\w+)");

    private final RestTemplate restTemplate;

    public Consumer(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public void consume(Bot bot) {
        Integer direction = runInSandbox(bot);
        if (direction == null) {
            // 执行失败（编译错误/超时/崩溃）：不发送结果，对应玩家按超时判负，与旧实现一致
            log.warn("Bot 执行失败，userId={}", bot.getUserId());
            return;
        }

        MultiValueMap<String, String> data = new LinkedMultiValueMap<>();
        data.add("user_id", bot.getUserId().toString());
        data.add("direction", direction.toString());
        data.add("enemy_id", bot.getEnemy().toString());

        restTemplate.postForObject(receiveBotMoveUrl, data, String.class);
    }

    private Integer runInSandbox(Bot bot) {
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("kob-bot-");
            // 用户 Bot 的约定：从工作目录读取 input.txt
            Files.writeString(tempDir.resolve("input.txt"), bot.getInput(), StandardCharsets.UTF_8);

            String fqn = resolveBotClassName(bot.getBotCode());
            if (fqn == null) {
                log.warn("无法从 Bot 源码中解析类名，userId={}", bot.getUserId());
                return null;
            }

            if (!compile(tempDir, bot.getBotCode(), fqn)) {
                return null;
            }

            return runChildProcess(tempDir, fqn);
        } catch (Exception e) {
            log.error("Bot 沙箱执行异常，userId={}", bot.getUserId(), e);
            return null;
        } finally {
            deleteRecursively(tempDir);
        }
    }

    /** 从用户源码中解析 package + public class 名，得到全限定类名 */
    private String resolveBotClassName(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String className = "Bot";
        Matcher classMatcher = CLASS_PATTERN.matcher(code);
        if (classMatcher.find()) {
            className = classMatcher.group(1);
        }
        String packageName = "";
        Matcher packageMatcher = PACKAGE_PATTERN.matcher(code);
        if (packageMatcher.find()) {
            packageName = packageMatcher.group(1) + ".";
        }
        return packageName + className;
    }

    private boolean compile(Path tempDir, String botCode, String fqn) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            log.error("当前 JVM 不含编译器（需要 JDK 而非 JRE 运行服务）");
            return false;
        }

        try {
            String className = fqn.substring(fqn.lastIndexOf('.') + 1);
            Path botFile = tempDir.resolve(className + ".java");
            Files.writeString(botFile, botCode, StandardCharsets.UTF_8);

            // 启动器：实例化用户 Bot 并把 get() 结果写入 result.txt（不占用 stdout，用户可随意打印调试信息）
            Path launcherFile = tempDir.resolve("SandboxLauncher.java");
            Files.writeString(launcherFile,
                    "public final class SandboxLauncher {\n" +
                            "    public static void main(String[] args) throws Exception {\n" +
                            "        Integer direction = new " + fqn + "().get();\n" +
                            "        java.nio.file.Files.writeString(java.nio.file.Path.of(\"result.txt\"), String.valueOf(direction));\n" +
                            "    }\n" +
                            "}\n",
                    StandardCharsets.UTF_8);

            DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
            try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
                Iterable<? extends JavaFileObject> units = fileManager.getJavaFileObjectsFromFiles(
                        List.of(botFile.toFile(), launcherFile.toFile()));
                boolean ok = compiler.getTask(null, fileManager, diagnostics,
                        List.of("-proc:none", "-nowarn", "-d", tempDir.toString()),
                        null, units).call();
                if (!ok) {
                    for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
                        log.warn("Bot 编译错误：{} {}", d.getLineNumber(), d.getMessage(null));
                    }
                    return false;
                }
            }
            return true;
        } catch (IOException e) {
            log.error("Bot 编译 IO 异常", e);
            return false;
        }
    }

    private Integer runChildProcess(Path tempDir, String fqn) throws IOException, InterruptedException {
        String javaBin = resolveJavaBinary();
        ProcessBuilder pb = new ProcessBuilder(javaBin,
                "-Xmx128m",
                "-cp", tempDir.toString(),
                "SandboxLauncher");
        pb.directory(tempDir.toFile());
        // 清空环境变量，子进程无法读到父进程的数据库密码等配置
        pb.environment().clear();
        Path stdout = tempDir.resolve("stdout.txt");
        Path stderr = tempDir.resolve("stderr.txt");
        pb.redirectOutput(stdout.toFile());
        pb.redirectError(stderr.toFile());

        Process process = pb.start();
        try {
            if (!process.waitFor(BOT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                process.waitFor(2, TimeUnit.SECONDS);
                log.warn("Bot 执行超时（{} ms）已被终止，类：{}", BOT_TIMEOUT_MS, fqn);
                return null;
            }
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            return null;
        }

        if (process.exitValue() != 0) {
            log.warn("Bot 子进程异常退出，exitCode={}，stderr：{}", process.exitValue(), readTail(stderr));
            return null;
        }

        Path result = tempDir.resolve("result.txt");
        if (!Files.exists(result)) {
            log.warn("Bot 子进程正常退出但未产生结果（get() 可能抛出异常），stderr：{}", readTail(stderr));
            return null;
        }
        try {
            return Integer.parseInt(Files.readString(result).trim());
        } catch (NumberFormatException e) {
            log.warn("Bot 返回结果不是整数：{}", readTail(result));
            return null;
        }
    }

    private String resolveJavaBinary() {
        String exe = System.getProperty("os.name", "").toLowerCase().contains("win") ? "java.exe" : "java";
        Path bin = Path.of(System.getProperty("java.home"), "bin", exe);
        return Files.exists(bin) ? bin.toString() : "java";
    }

    private String readTail(Path file) {
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8).trim();
            return content.length() > 500 ? content.substring(content.length() - 500) : content;
        } catch (IOException e) {
            return "<无法读取>";
        }
    }

    private void deleteRecursively(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (var walk = Files.walk(dir)) {
            walk.sorted(java.util.Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
        } catch (IOException e) {
            log.warn("清理 Bot 临时目录失败：{}", dir, e);
        }
    }
}
