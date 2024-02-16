############
# OpenSSL compile
############
FROM debian:11
ARG OPENSSL_VERSION=3.0.8
ARG OPENSSL_CONFIGOPTS=enable-fips
ENV BUILD=/build
ENV INSTALL=/openssl
RUN apt-get update && apt-get install -y \
    make gcc wget perl
RUN mkdir -p /build && \
    wget "https://github.com/openssl/openssl/releases/download/openssl-$OPENSSL_VERSION/openssl-$OPENSSL_VERSION.tar.gz" -O- | \
    tar -C $BUILD --strip-components=1 -xzf -
RUN (cd $BUILD && ./Configure --prefix=$INSTALL $OPENSSL_CONFIGOPTS && make && make install)
RUN tar -czvf /openssl.tar.gz $INSTALL && rm -fr $INSTALL $BUILD
