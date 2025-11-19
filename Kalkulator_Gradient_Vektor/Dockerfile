FROM openjdk:17-jdk-slim

# Set working directory inside container
WORKDIR /app

# Copy jar dan library
COPY app.jar /app/app.jar
COPY lib /app/lib

# Expose port untuk Render
EXPOSE 8080

# Jalankan server
CMD ["java", "-cp", "app.jar:lib/*", "Main"]
