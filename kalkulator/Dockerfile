FROM eclipse-temurin:17-jdk

WORKDIR /app

COPY gradien.jar .
COPY lib ./lib

EXPOSE 8080

CMD ["java", "-cp", "gradien.jar:lib/*", "Main"]
