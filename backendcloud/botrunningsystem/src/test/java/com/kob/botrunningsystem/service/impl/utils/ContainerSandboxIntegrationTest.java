package com.kob.botrunningsystem.service.impl.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.nio.file.Files;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/** 构建 system/bot-sandbox 镜像后，设置 KOB_RUN_SANDBOX_TESTS=true 运行。 */
@EnabledIfEnvironmentVariable(named = "KOB_RUN_SANDBOX_TESTS", matches = "true")
class ContainerSandboxIntegrationTest {
    private final ContainerSandbox sandbox = new ContainerSandbox("docker", "kob-bot-sandbox:17");

    @Test
    void validBotReadsInputAndReturnsMove() {
        assertTrue(sandbox.available(), "Build the Linux sandbox image first");
        assertEquals(2, sandbox.execute("public class Bot { public Integer get() throws Exception {"
                + "return java.nio.file.Files.readString(java.nio.file.Path.of(\"input.txt\")).equals(\"hello\") ? 2 : 0; }}", "hello"));
    }

    @Test
    void cannotReadHostFileOrReachHostNetwork() throws Exception {
        var marker = Files.createTempFile("kob-host-marker-", ".txt");
        try {
            Files.writeString(marker, "host-only");
            String uri = marker.toUri().toString();
            String code = "public class Bot { public Integer get() throws Exception {"
                    + "if(java.nio.file.Files.exists(java.nio.file.Path.of(java.net.URI.create(\"" + uri + "\")))) return 3;"
                    + "try(java.net.Socket s=new java.net.Socket()){ s.connect(new java.net.InetSocketAddress(\"1.1.1.1\",443),200); return 3; }"
                    + "catch(java.io.IOException expected){return 0;} }}";
            assertEquals(0, sandbox.execute(code, "input"));
        } finally {
            Files.deleteIfExists(marker);
        }
    }

    @Test
    void infiniteLoopIsTerminated() {
        assertTimeoutPreemptively(Duration.ofSeconds(8), () ->
                assertNull(sandbox.execute("public class Bot { public Integer get() { while(true){} }}", "input")));
    }
}
