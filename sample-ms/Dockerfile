FROM openjdk:jre-alpine
COPY target/sample-ms.jar application.yml /
EXPOSE 80
CMD ["/usr/bin/java", "-jar", "/sample-ms.jar"]
HEALTHCHECK CMD wget -qO - http://localhost/.well-known/manifest || exit 1