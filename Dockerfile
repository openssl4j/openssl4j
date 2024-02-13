############
# OpenSSL compile
############
FROM debian:11 as openssl
RUN apt-get update && apt-get install -y \
make gcc wget perl
RUN wget "https://github.com/openssl/openssl/releases/download/openssl-3.0.8/openssl-3.0.8.tar.gz" && mkdir -p ./build && tar -C build -xzf openssl-3.0.8.tar.gz
RUN (cd build/openssl-3.0.8 && ./Configure --prefix=/openssl enable-fips && make && make install)

############
# OpenSSL4j
############
FROM debian:11 as openssl4j

ENV JAVA_HOME=/opt/java/openjdk
COPY --from=eclipse-temurin:21 $JAVA_HOME $JAVA_HOME
COPY --from=openssl /openssl/ /openssl

ENV PATH="${JAVA_HOME}/bin:${PATH}"

RUN apt-get update && apt-get install -y \
make gcc
COPY . openssl4j
ENV JAVA_HOME=/opt/java/openjdk/
RUN echo "JAVA_HOME    is ${JAVA_HOME}"
RUN echo "OS_ARCH      is $(cd openssl4j/build-helper && ${JAVA_HOME}/bin/java -Xint OsArch.java)"

RUN echo "OpenSSL header files: "
RUN find / -name "provider.h"
RUN find / -name "core.h"
RUN find / -name "libssl.so.*"
RUN ls -lah ./
RUN cd openssl4j && \
make
RUN cd ./openssl4j/openssl4j_build/openssl4j/c && ls -al
