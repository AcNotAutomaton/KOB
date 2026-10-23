package com.kob.botrunningsystem.service.impl.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** 无宿主机挂载、无网络、无特权的短生命周期容器，包含编译和执行。 */
@Component
public class ContainerSandbox {
    private static final Logger log = LoggerFactory.getLogger(ContainerSandbox.class);
    private static final int OUTPUT_LIMIT = 4096;
    private final String command;
    private final String image;

    @Autowired
    public ContainerSandbox(@Value("${kob.bot.sandbox-command:docker}") String command,
                            @Value("${kob.bot.sandbox-image:kob-bot-sandbox:17}") String image) {
        this.command = command;
        this.image = image;
    }

    public boolean available() {
        Result result = run(List.of(command, "image", "inspect", "--format={{.Os}}", image), "", 2000);
        return result != null && result.exitCode == 0 && "linux".equals(result.output);
    }

    public Integer execute(String code, String input) {
        if (code == null || code.isBlank() || code.length() > 10000 || input == null || input.length() > 16000) return null;
        String name = "kob-bot-" + UUID.randomUUID();
        boolean stopped = false;
        try {
            Result result = run(commandFor(name), encode(code) + "\n" + encode(input) + "\n", 4000);
            stopped = result != null && result.exitCode == 0;
            if (result == null || result.exitCode != 0 || !result.output.matches("KOB_RESULT:-?\\d+\\s*")) return null;
            return Integer.valueOf(result.output.substring("KOB_RESULT:".length()).trim());
        } catch (NumberFormatException e) {
            return null;
        } finally {
            // 客户端退出/超时不等于容器退出；显式移除容器及其全部子进程。
            if (!stopped) {
                boolean interrupted = Thread.interrupted();
                try {
                    run(List.of(command, "rm", "--force", name), "", 2000);
                } finally {
                    if (interrupted) Thread.currentThread().interrupt();
                }
            }
        }
    }

    List<String> commandFor(String name) {
        return List.of(command, "run", "--rm", "--pull=never", "--name", name, "--interactive",
                "--network=none", "--read-only", "--user=65534:65534", "--cap-drop=ALL",
                "--security-opt=no-new-privileges", "--pids-limit=64", "--memory=256m",
                "--memory-swap=256m", "--cpus=1", "--ulimit=nofile=64:64",
                "--ulimit=fsize=1048576:1048576", "--log-driver=none", "--workdir=/work",
                "--tmpfs=/work:rw,nosuid,nodev,noexec,size=32m,mode=1777", image);
    }

    private static String encode(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    Process startProcess(List<String> args) throws IOException {
        return new ProcessBuilder(args).redirectErrorStream(true).start();
    }

    private record Result(int exitCode, String output) { }

    private Result run(List<String> args, String input, long timeoutMs) {
        Process process = null;
        try {
            process = startProcess(args);
            Process child = process;
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            boolean[] overflow = {false};
            Thread reader = new Thread(() -> {
                try (var stream = child.getInputStream()) {
                    byte[] buffer = new byte[1024];
                    int count;
                    while ((count = stream.read(buffer)) != -1) {
                        if (output.size() + count <= OUTPUT_LIMIT) output.write(buffer, 0, count);
                        else overflow[0] = true;
                    }
                } catch (IOException ignored) { }
            }, "sandbox-output");
            reader.setDaemon(true);
            reader.start();
            // Docker 连接 daemon 或启动容器可能阻塞，写 stdin 也必须受执行超时约束。
            Thread writer = new Thread(() -> {
                try (var stdin = child.getOutputStream()) {
                    stdin.write(input.getBytes(StandardCharsets.UTF_8));
                } catch (IOException ignored) { }
            }, "sandbox-input");
            writer.setDaemon(true);
            writer.start();
            if (!process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                return null;
            }
            reader.join(500);
            writer.join(500);
            if (reader.isAlive() || writer.isAlive() || overflow[0]) return null;
            return new Result(process.exitValue(), output.toString(StandardCharsets.UTF_8).trim());
        } catch (IOException e) {
            log.warn("Bot 容器不可用，请检查 Docker 和沙箱镜像：{}", e.getMessage());
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } finally {
            if (process != null) {
                if (process.isAlive()) process.destroyForcibly();
                try { process.getInputStream().close(); } catch (IOException ignored) { }
            }
        }
    }
}
