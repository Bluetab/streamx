# ---- Copy Files/Build ----
FROM maven:3.6-jdk-8-alpine AS build

MAINTAINER True-Dat Dev Team

ARG APP_VERSION

ENV VERSION=${APP_VERSION}

RUN mkdir /build
WORKDIR /build
COPY . /build

RUN mvn -DskipTests package


# --- Release ----
FROM confluentinc/cp-kafka:3.0.0

ARG APP_VERSION

RUN apt-get update && apt-get install -y vim

ENV STREAMX_DIR /usr/local/streamx

COPY --from=build /build/target/streamx-${APP_VERSION}-development/share/java/streamx $STREAMX_DIR

ADD config $STREAMX_DIR/config
ADD docker/entry $STREAMX_DIR/entry
ADD docker/utils.py $STREAMX_DIR/utils.py

EXPOSE 8084

ENV CLASSPATH=$CLASSPATH:$STREAMX_DIR/*

RUN chmod 777 $STREAMX_DIR/entry && mkdir /tmp/streamx-logs
CMD ["bash","-c","$STREAMX_DIR/entry"]
