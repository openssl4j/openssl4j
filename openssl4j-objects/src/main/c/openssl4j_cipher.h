#ifndef OPENSSL4J_CIPHER
#define OPENSSL4J_CIPHER
#include <openssl/evp.h>

int initCipher(const char* cName,const unsigned char* cKey,int cKeyLen, unsigned char * cIV, int cIVLen, EVP_CIPHER_CTX * cipherctx, long libCtxInt, int encrypt);
int opensslUpdate(EVP_CIPHER_CTX *cipherctx, const unsigned char * bytesIn, unsigned char * bytesOut, int inLen, int* outLen, int encrypt);
int opensslDoFinal(EVP_CIPHER_CTX *cipherctx, unsigned char* bytesOut, int outputOffset, int* outputLen, int encrypt);

#endif

// This header file is created as an interface for C tests and programs. It is safe to include at compile time even if it winds up not being used. We have used it in the past, and might need it in the future.