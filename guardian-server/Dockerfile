FROM gradle:8.9-jdk17 AS build
WORKDIR /workspace
COPY . .
RUN gradle :guardian-server:installDist --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/guardian-server/build/install/guardian-server/ /app/

EXPOSE 10000
CMD ["/app/bin/guardian-server"]
