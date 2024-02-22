####
#
# Makefile for generating the native C library
#
####
JAVA_OS_ARCH:=$(shell cd build-helper && ${JAVA_HOME}/bin/javac OsArch.java && ${JAVA_HOME}/bin/java -cp . OsArch)

$(info JavaArch: ${JAVA_OS_ARCH})

BASE_DIR?=openssl4j_build

JNI_JAVA_SOURCES=openssl4j/src/main/java
JNI_C_SOURCES=openssl4j/src/main/c
JNI_C_TEST_SOURCES=openssl4j/src/test/c
TARGET=${BASE_DIR}/openssl4j/c
INSTALL_TARGET=openssl4j/src/main/resources/objects
JNI_JAVA_FILES=${JNI_JAVA_SOURCES}/de/sfuhrm/openssl4j/OpenSSLMessageDigestNative.java ${JNI_JAVA_SOURCES}/de/sfuhrm/openssl4j/OpenSSLCipherNative.java ${JNI_JAVA_SOURCES}/de/sfuhrm/openssl4j/OpenSSLCryptoNative.java ${JNI_JAVA_SOURCES}/de/sfuhrm/openssl4j/OpenSSLSecureRandomNative.java ${JNI_JAVA_SOURCES}/de/sfuhrm/openssl4j/OpenSSLMacNative.java
JNI_HEADER_FILES=${TARGET}/include/de_sfuhrm_openssl4j_OpenSSLMessageDigestNative.h ${TARGET}/include/de_sfuhrm_openssl4j_OpenSSLCipherNative.h ${TARGET}/include/de_sfuhrm_openssl4j_OpenSSLCryptoNative.h ${TARGET}/include/de_sfuhrm_openssl4j_OpenSSLSecureRandomNative.h ${TARGET}/include/de_sfuhrm_openssl4j_OpenSSLMacNative.h

UNAME_S := $(shell uname -s)

libs:=
test_libs := ${TARGET}/libopenssl4j-${JAVA_OS_ARCH}.so

INCLUDES= -I${TARGET}/include/ -I${JAVA_HOME}/include -I${JAVA_HOME}/include/linux -I/usr/local/include
TEST_INCLUDES = -I${TARGET}/include/ -I${JNI_C_SOURCES}/ -I${JAVA_HOME}/include -I/usr/local/include

$(info Target: ${TARGET})

ifeq ("${JAVA_OS_ARCH}", "Mac_OS_X-aarch64")
$(info Building for Apple arm)
INCLUDES+= -I${JAVA_HOME}/include/darwin -I${BASE_DIR}/OpenSSL/include
TEST_INCLUDES+= -I${JAVA_HOME}/include/darwin -I${BASE_DIR}/OpenSSL/include
libs+= -L${BASE_DIR}/OpenSSL/lib/ ${BASE_DIR}/OpenSSL/lib/libssl.dylib ${BASE_DIR}/OpenSSL/lib/libcrypto.dylib 
else ifeq ("${JAVA_OS_ARCH}", "Mac_OS_X-x86_64")
$(info Building for Apple x86)
INCLUDES+= -I${JAVA_HOME}/include/darwin -I${BASE_DIR}/OpenSSL/include
TEST_INCLUDES+= -I${JAVA_HOME}/include/darwin -I${BASE_DIR}/OpenSSL/include
libs+= -L${BASE_DIR}/OpenSSL/lib/ ${BASE_DIR}/OpenSSL/lib/libssl.dylib ${BASE_DIR}/OpenSSL/lib/libcrypto.dylib
else
$(info Building for Linux x86)
TEST_INCLUDES+= -I${JAVA_HOME}/include/linux
libs+= -L/lib64/ -l:libssl.so.3 -l:libcrypto.so.3
endif

.PHONY: all
.PHONY: clean
.PHONY: install

install: ${TARGET}/libopenssl4j-${JAVA_OS_ARCH}.so
	mkdir -p ${INSTALL_TARGET}
	cp $< ${INSTALL_TARGET} 
	cp ./openssl4j/src/main/java/de/sfuhrm/openssl4j/OpenSSLCryptoNative.java temp.java
	ls -lah ./openssl4j/src/main/java/de/sfuhrm/openssl4j/
	sed 's@private static String openssl4JBasePath = "\/openssl4j";@private static String openssl4JBasePath = "\${BASE_DIR}";@g' ./temp.java > ./openssl4j/src/main/java/de/sfuhrm/openssl4j/OpenSSLCryptoNative.java
clean:
	rm -fr ${TARGET} ${INSTALL_TARGET}

${TARGET}/include/%.h: ${JNI_JAVA_FILES}
	 mkdir -p ${TARGET}/include
	 ${JAVA_HOME}/bin/javac -J-Xint -classpath ${JNI_JAVA_SOURCES} -h ${TARGET}/include -d ${TARGET} -s ${TARGET} ${JNI_JAVA_FILES}

${TARGET}/libopenssl4j-${JAVA_OS_ARCH}.so: ${JNI_HEADER_FILES}
	$(info Includes: ${INCLUDES})
ifeq ($(UNAME_S),Darwin)
	 gcc -Wall -Werror -fPIC -o "$@" -lc -Wl,-v \
	-Wl,-rpath,/usr/local/lib64,-rpath,@loader_path ${libs} -shared ${INCLUDES} \
	${JNI_C_SOURCES}/openssl4j_common.c \
	${JNI_C_SOURCES}/openssl4j_messagedigest.c \
	${JNI_C_SOURCES}/openssl4j_cipher.c \
	${JNI_C_SOURCES}/openssl4j_crypto.c \
	${JNI_C_SOURCES}/openssl4j_secureRandom.c \
	${JNI_C_SOURCES}/openssl4j_mac.c
	$(info Output File: "$@")
else	
	 gcc -Wall -Werror -fPIC -o "$@" -lc -Wl,-v \
	-Wl,-z,defs -Wl,-rpath,/lib64/ossl-modules,-rpath,'$$ORIGIN',-rpath,/lib64 -Wl,-z,origin -shared ${INCLUDES} \
	${JNI_C_SOURCES}/openssl4j_common.c \
	${JNI_C_SOURCES}/openssl4j_messagedigest.c \
	${JNI_C_SOURCES}/openssl4j_cipher.c \
	${JNI_C_SOURCES}/openssl4j_crypto.c \
	${JNI_C_SOURCES}/openssl4j_secureRandom.c \
	${JNI_C_SOURCES}/openssl4j_mac.c ${libs}
	$(info Output File: "$@")
endif