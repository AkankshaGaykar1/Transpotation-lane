# ---- build
FROM eclipse-temurin:17-jdk AS build
WORKDIR /build
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src src
# tests run in CI, not in the image build
RUN ./mvnw -B -q -DskipTests package

# ---- run
FROM eclipse-temurin:17-jre
RUN useradd --system --uid 1001 app
USER app
WORKDIR /app
COPY --from=build /build/target/app.jar app.jar
# small heap, thread stacks and code cache so the whole process stays under 512 MB (render's free plan).
# if the heap runs out the jvm exits and render restarts it, better than a half-dead process
ENV JAVA_TOOL_OPTIONS="-Xmx192m -Xss512k -XX:ReservedCodeCacheSize=48m -XX:MaxMetaspaceSize=128m -XX:+UseSerialGC -XX:MinHeapFreeRatio=10 -XX:MaxHeapFreeRatio=30 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
