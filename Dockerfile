FROM debian:11

# for example 3.0.8
ARG OPENSSL_VERSION=3.0.8
# plain or fips
ARG OPENSSL_NAME=fips
# url for downloading the platform binaries
ARG OPENSSL_URL="https://github.com/openssl4j/openssl-crossplatform/releases/download/v20240217200647/openssl-linux_amd64-3.0.8-fips.tar.gz"

ENV JAVA_HOME=/opt/java/openjdk
ENV SRC_DIR=/openssl4j
COPY --from=eclipse-temurin:11 $JAVA_HOME $JAVA_HOME
ENV PATH="${JAVA_HOME}/bin:${PATH}"

RUN apt-get update && apt-get install -y \
make gcc wget perl
COPY . $SRC_DIR
ENV JAVA_HOME=/opt/java/openjdk/
RUN echo "JAVA_HOME    is ${JAVA_HOME}"
RUN echo "OS_ARCH      is $(cd $SRC_DIR/build-helper && ${JAVA_HOME}/bin/java -Xint OsArch.java)"
RUN cd /; wget "$OPENSSL_URL" -O- | tar -xvzf -
RUN echo "OpenSSL header files: "
RUN find / -name "provider.h"
RUN find / -name "core.h"
RUN find / -name "libssl.so.*"
RUN ls -lah ./
RUN cd $SRC_DIR && \
make
RUN cd $SRC_DIR/openssl4j_build/openssl4j/c && ls -al
