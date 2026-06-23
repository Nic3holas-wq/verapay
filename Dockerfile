FROM eclipse-temurin:21-jdk

WORKDIR /app

COPY target/verapay-deployment.jar /app/verapay.jar

EXPOSE 8080

ENV JAVA_OPTS=""
CMD ["sh", "-c", "java $JAVA_OPTS -jar /app/verapay.jar"]