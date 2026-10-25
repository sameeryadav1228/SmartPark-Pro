
FROM eclipse-temurin:21-jdk
WORKDIR /app
COPY . .
RUN javac Main.java HttpUtils.java ParkingService.java Vehicle.java
EXPOSE 8080
CMD ["java", "Main"]
