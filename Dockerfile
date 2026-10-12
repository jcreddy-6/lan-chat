# Render has no native Java runtime, so the server runs from this Docker image.
FROM eclipse-temurin:25-jdk
WORKDIR /app

# Your own code and the SQLite driver that is already in lib/
COPY src ./src
COPY lib ./lib

# WebSocket library (and the logging API it needs), fetched from Maven Central at build time
ADD https://repo1.maven.org/maven2/org/java-websocket/Java-WebSocket/1.5.7/Java-WebSocket-1.5.7.jar lib/
ADD https://repo1.maven.org/maven2/org/slf4j/slf4j-api/2.0.16/slf4j-api-2.0.16.jar lib/

# Compile everything into out/
RUN mkdir out && javac -cp "lib/*" -d out $(find src -name "*.java")

# Render sets the PORT variable; the server reads it. Set DB_PATH=/data/lanchat.db if you attach a disk.
CMD ["java", "-cp", "out:lib/*", "com.lanchat.server.ChatServer"]
