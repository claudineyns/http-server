FROM alpine:3.16.2 as builder

RUN apk add --update wget zip openjdk8

RUN wget --output-document=/tmp/maven.zip https://dlcdn.apache.org/maven/maven-3/3.8.6/binaries/apache-maven-3.8.6-bin.zip \
  && unzip /tmp/maven.zip -d /tmp/ \
  && mkdir -p /opt/maven/ \
  && mv /tmp/apache-maven-3.8.6/* /opt/maven/ \
  && rm -Rf /tmp/apache-maven-3.8.6/

RUN wget --output-document=/tmp/http-server.zip https://github.com/claudineyns/http-server/archive/refs/heads/master.zip \
  && unzip /tmp/http-server.zip -d /tmp/ \
  && mv /tmp/http-server-main/ /tmp/source/ \
  && cd /tmp/source && /opt/maven/bin/mvn install \
  && mv /tmp/source/target/*-shaded.jar /tmp/runner.jar

FROM alpine:3.16.2

RUN mkdir /app

COPY --from=builder /tmp/runner.jar /app/runner.jar

RUN apk add --update openjdk8-jre-base

ENTRYPOINT ["/usr/bin/java","-jar","/app/runner.jar"]
