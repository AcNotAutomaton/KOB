package com.kob.botrunningsystem.service.impl.utils;

import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ContainerSandboxTest {
    private Process process(String output, int exitCode, boolean finished) throws Exception {
        Process process = mock(Process.class);
        when(process.getInputStream()).thenReturn(new ByteArrayInputStream(output.getBytes(StandardCharsets.UTF_8)));
        when(process.getOutputStream()).thenReturn(new ByteArrayOutputStream());
        when(process.waitFor(anyLong(), eq(TimeUnit.MILLISECONDS))).thenReturn(finished);
        when(process.exitValue()).thenReturn(exitCode);
        return process;
    }

    @Test
    void compilesAndRunsOnlyInsideResourceLimitedContainer() throws Exception {
        Process child = process("KOB_RESULT:2\n", 0, true);
        List<List<String>> calls = new ArrayList<>();
        ContainerSandbox sandbox = new ContainerSandbox("docker", "kob-bot-sandbox:17") {
            @Override Process startProcess(List<String> args) { calls.add(args); return child; }
        };
        assertEquals(2, sandbox.execute("public class Bot {}", "input"));
        assertEquals(1, calls.size());
        List<String> command = calls.get(0);
        assertTrue(command.containsAll(List.of("--network=none", "--read-only", "--user=65534:65534",
                "--cap-drop=ALL", "--security-opt=no-new-privileges", "--memory=256m", "--pids-limit=64", "--cpus=1")));
        assertFalse(command.stream().anyMatch(arg -> arg.startsWith("--mount") || arg.startsWith("--volume") || arg.startsWith("--env")));
        String[] stdin = processInput(child).split("\n");
        assertEquals("public class Bot {}", new String(Base64.getDecoder().decode(stdin[0]), StandardCharsets.UTF_8));
        assertEquals("input", new String(Base64.getDecoder().decode(stdin[1]), StandardCharsets.UTF_8));
    }

    private String processInput(Process child) throws IOException {
        return child.getOutputStream().toString();
    }

    @Test
    void missingDockerFailsClosedWithoutAnyHostJavaFallback() {
        List<List<String>> calls = new ArrayList<>();
        ContainerSandbox sandbox = new ContainerSandbox("missing-docker", "image") {
            @Override Process startProcess(List<String> args) throws IOException {
                calls.add(args);
                throw new IOException("not installed");
            }
        };
        assertFalse(sandbox.available());
        assertNull(sandbox.execute("public class Bot {}", "input"));
        assertTrue(calls.stream().allMatch(command -> "missing-docker".equals(command.get(0))));
    }

    @Test
    void timeoutKillsCliAndExplicitlyRemovesContainerAndDescendants() throws Exception {
        Process child = process("", 0, false), cleanup = process("", 0, true);
        List<List<String>> calls = new ArrayList<>();
        ContainerSandbox sandbox = new ContainerSandbox("docker", "image") {
            @Override Process startProcess(List<String> args) {
                calls.add(args);
                return args.contains("run") ? child : cleanup;
            }
        };
        assertNull(sandbox.execute("public class Bot {}", "input"));
        verify(child).destroyForcibly();
        String name = calls.get(0).get(calls.get(0).indexOf("--name") + 1);
        assertEquals(List.of("docker", "rm", "--force", name), calls.get(1));
    }

    @Test
    void outputFloodIsRejectedWithoutUnboundedBuffering() throws Exception {
        Process child = process("x".repeat(100000), 0, true), cleanup = process("", 0, true);
        ContainerSandbox sandbox = new ContainerSandbox("docker", "image") {
            @Override Process startProcess(List<String> args) { return args.contains("run") ? child : cleanup; }
        };
        assertNull(sandbox.execute("public class Bot {}", "input"));
    }

    @Test
    void healthRequiresLinuxImage() throws Exception {
        Process child = process("linux\n", 0, true);
        ContainerSandbox sandbox = new ContainerSandbox("docker", "image") {
            @Override Process startProcess(List<String> args) { return child; }
        };
        assertTrue(sandbox.available());
    }

    @Test
    void blockedStdinStillObeysExecutionTimeout() throws Exception {
        Process child = process("", 0, false), cleanup = process("", 0, true);
        CountDownLatch killed = new CountDownLatch(1);
        when(child.getOutputStream()).thenReturn(new OutputStream() {
            @Override public void write(int value) throws IOException {
                try {
                    if (!killed.await(2, TimeUnit.SECONDS)) throw new IOException("writer was not stopped");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException(e);
                }
            }
        });
        when(child.destroyForcibly()).thenAnswer(inv -> { killed.countDown(); return child; });
        ContainerSandbox sandbox = new ContainerSandbox("docker", "image") {
            @Override Process startProcess(List<String> args) { return args.contains("run") ? child : cleanup; }
        };
        assertTimeoutPreemptively(Duration.ofSeconds(1), () ->
                assertNull(sandbox.execute("public class Bot {}", "input")));
        assertEquals(0, killed.getCount());
    }
}
