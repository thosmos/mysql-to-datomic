FROM java:8-alpine
MAINTAINER Thomas Spellman <thomas@thosmos.com>

ADD target/mysql-to-datomic.jar /app.jar

CMD ["java", "-jar", "/app.jar"]
