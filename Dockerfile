FROM alpine:3.18.2 as builder

RUN apk add --update wget zip openjdk17-jdk

ARG MAVEN=3.9.3

RUN wget --output-document=/tmp/maven.zip https://dlcdn.apache.org/maven/maven-3/${MAVEN}/binaries/apache-maven-${MAVEN}-bin.zip \
  && unzip /tmp/maven.zip -d /tmp/ \
  && mkdir -p /opt/maven/ \
  && mv /tmp/apache-maven-${MAVEN}/* /opt/maven/ \
  && rm -Rf /tmp/apache-maven-${MAVEN}/

RUN wget --output-document=/tmp/http-server.zip https://github.com/claudineyns/http-server/archive/refs/heads/master.zip \
  && unzip /tmp/http-server.zip -d /tmp/ \
  && mv /tmp/http-server-main/ /tmp/source/ \
  && cd /tmp/source && /opt/maven/bin/mvn install \
  && mv /tmp/source/target/*-shaded.jar /tmp/runner.jar

FROM alpine:3.18.2

RUN mkdir /app

COPY --from=builder /tmp/runner.jar /app/runner.jar

RUN apk add --update openjdk17-jre

ENTRYPOINT ["/usr/bin/java","-jar","/app/runner.jar"]
