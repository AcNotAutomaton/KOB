import javax.tools.ToolProvider;
import java.io.*;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

/** 只在受限容器内运行；协议为两行 Base64（源码、input.txt）。 */
public final class SandboxMain {
    public static void main(String[] args) throws Exception {
        PrintStream resultOut = System.out;
        BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        String code = decode(stdin.readLine());
        String input = decode(stdin.readLine());
        if (code.length() > 10000 || input.length() > 16000) System.exit(2);
        var classMatcher = Pattern.compile("public\\s+class\\s+(\\w+)").matcher(code);
        if (!classMatcher.find()) System.exit(2);
        String name = classMatcher.group(1);
        var packageMatcher = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE).matcher(code);
        String fqn = packageMatcher.find() ? packageMatcher.group(1) + "." + name : name;
        Path work = Path.of("/work");
        Path source = work.resolve(name + ".java");
        Files.writeString(source, code, StandardCharsets.UTF_8);
        Files.writeString(work.resolve("input.txt"), input, StandardCharsets.UTF_8);
        var compiler = ToolProvider.getSystemJavaCompiler();
        try (var manager = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
            boolean ok = compiler.getTask(new StringWriter(), manager, null,
                    List.of("-proc:none", "--release", "17", "-classpath", work.toString(), "-d", work.toString()),
                    null, manager.getJavaFileObjects(source.toFile())).call();
            if (!ok) System.exit(2);
        }
        System.setOut(new PrintStream(OutputStream.nullOutputStream()));
        System.setErr(new PrintStream(OutputStream.nullOutputStream()));
        try (URLClassLoader loader = new URLClassLoader(new java.net.URL[]{work.toUri().toURL()},
                ClassLoader.getPlatformClassLoader())) {
            Class<?> bot = Class.forName(fqn, true, loader);
            Object direction = bot.getMethod("get").invoke(bot.getConstructor().newInstance());
            if (!(direction instanceof Integer)) System.exit(2);
            resultOut.println("KOB_RESULT:" + direction);
        }
    }

    private static String decode(String value) {
        return new String(Base64.getDecoder().decode(Objects.requireNonNull(value)), StandardCharsets.UTF_8);
    }
}
