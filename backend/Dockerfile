FROM bellsoft/liberica-native-image-kit-container:jdk-21-nik-23.1.12.1-glibc AS build

WORKDIR /workspace

COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN ./gradlew --no-daemon --quiet dependencies --configuration runtimeClasspath > /dev/null

COPY src/main src/main
RUN ./gradlew --no-daemon -Porg.gradle.java.installations.auto-download=false nativeCompile

FROM debian:12-slim AS runtime-libs

RUN mkdir /runtime-libs && cp -L /usr/lib/*-linux-gnu/libz.so.1 /runtime-libs/

FROM gcr.io/distroless/base-debian12:nonroot

COPY --from=runtime-libs /runtime-libs/ /usr/lib/

COPY --from=build /workspace/build/native/nativeCompile/sixteen-screener-backend /app/sixteen-screener-backend

ENV PORT=8080
EXPOSE 8080
USER nonroot:nonroot
ENTRYPOINT ["/app/sixteen-screener-backend"]
