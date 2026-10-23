FROM eclipse-temurin:17-jdk
WORKDIR /opt/kob
COPY SandboxMain.java .
RUN javac SandboxMain.java && rm SandboxMain.java
USER 65534:65534
WORKDIR /work
ENTRYPOINT ["java", "-Xmx128m", "-XX:MaxMetaspaceSize=64m", "-XX:ReservedCodeCacheSize=32m", "-XX:+UseSerialGC", "-XX:ActiveProcessorCount=1", "-XX:-UsePerfData", "-Djava.io.tmpdir=/work", "-cp", "/opt/kob", "SandboxMain"]
