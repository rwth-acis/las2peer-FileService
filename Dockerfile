FROM gradle:6.7-jdk14 as build

COPY . /home/gradle/src
RUN true
WORKDIR /home/gradle/src

RUN gradle export -x test

# Build final container without build dependencies etc.
FROM openjdk:14-jdk-alpine

ENV HTTP_PORT=8080
ENV HTTPS_PORT=8443
ENV LAS2PEER_PORT=9011


RUN apk add --update bash curl tzdata  xmlstarlet && rm -f /var/cache/apk/*

RUN addgroup -g 1777 -S las2peer && \
    adduser -u 1777 -S las2peer -G las2peer

COPY --chown=las2peer:las2peer . /src
RUN true
WORKDIR /src
USER las2peer
RUN dos2unix gradlew
RUN dos2unix /src/docker-entrypoint.sh
RUN mkdir etc
RUN touch /src/etc/pastry.properties
COPY --chown=las2peer:las2peer --from=build /home/gradle/src/file_service/build/export/ .
RUN true
COPY --chown=las2peer:las2peer docker-entrypoint.sh /src/docker-entrypoint.sh
RUN true
COPY --chown=las2peer:las2peer gradle.properties /src/gradle.properties
RUN true
COPY --chown=las2peer:las2peer gradle.properties /src/etc/pastry.properties
RUN true
RUN chmod +x docker-entrypoint.sh

RUN dos2unix docker-entrypoint.sh
RUN dos2unix gradle.properties
# RUN dos2unix etc/ant_configuration/service.properties
# run the rest as unprivileged user
RUN chmod +x gradlew && ./gradlew build --exclude-task test



EXPOSE $HTTP_PORT
EXPOSE $HTTPS_PORT
EXPOSE $LAS2PEER_PORT
RUN chmod +x /src/docker-entrypoint.sh
RUN chmod +x docker-entrypoint.sh
ENTRYPOINT ["/src/docker-entrypoint.sh"]