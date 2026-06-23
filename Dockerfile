FROM eclipse-temurin:21-jdk

WORKDIR /app

COPY target/verapay-deployment.jar /app/verapay.jar

EXPOSE 8080

CMD ["java", "-jar", "verapay.jar"]