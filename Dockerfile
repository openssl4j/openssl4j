FROM debian:11
RUN apt-get update && apt-get install make
RUN apt-cache search openjdk
COPY . openssl4j
RUN cd openssl4j && make