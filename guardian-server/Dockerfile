FROM eclipse-temurin:17-jre

WORKDIR /app
COPY build/install/guardian-server/ /app/

EXPOSE 8080
CMD ["/app/bin/guardian-server"]
