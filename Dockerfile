FROM eclipse-temurin:25-jdk
WORKDIR /app

# Install Nginx and gettext-base for reverse proxying and web hosting
RUN apt-get update && \
    apt-get install -y --no-install-recommends nginx gettext-base dos2unix && \
    rm -rf /var/lib/apt/lists/*

# Copy source code, libraries, templates, and web assets
COPY src ./src
COPY lib ./lib
COPY public ./public
COPY nginx.conf.template ./nginx.conf.template
COPY entrypoint.sh ./entrypoint.sh

# Ensure executable permissions and Unix line endings
RUN dos2unix ./entrypoint.sh && chmod +x ./entrypoint.sh

# Compile Java sources and package the downloadable client JAR
RUN mkdir -p out && \
    javac -cp "lib/*" -d out $(find src -name "*.java") && \
    jar cfe public/LANChatClient.jar com.lanchat.client.ClientApp -C out .

EXPOSE 10000 5000

CMD ["./entrypoint.sh"]
