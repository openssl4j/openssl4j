package de.sfuhrm.openssl4j;

import java.io.Console;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.AlgorithmParameters;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.CipherSpi;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;

import de.sfuhrm.openssl4j.OpenSSLCryptoNative;

/**
 * An interface to OpenSSL cipher functions.
 *
 * @author Stephan Fuhrmann
 */
class OpenSSLCipherNative extends CipherSpi {

  /**
   * Get the list of Cipher algorithms supported by OpenSSL.
   *
   * @return an array of supported cipher algorithms from the OpenSSL library.
   */
  private static native String[] listCiphers();

  /**
   * Returns the context size in bytes. This is used to allocate the {@link #context direct
   * ByteBuffer}.
   *
   * @return a ByteBuffer containing the native cipher context.
   */
  private native ByteBuffer nativeContext();

  /**
   * Removes a context allocated with {@linkplain #nativeContext()}.
   *
   * @param context the context to free.
   */
  private static native void removeContext(ByteBuffer context);

  private static native int engineInitNative(
      ByteBuffer context, String name, byte[] key, byte[] iv, int encrypt, long libCtx, int paddingEnabled);

  private static native int getBlockSizeNative(ByteBuffer context, String name, long libCtx);

  private static native byte[] getOriginalIVNative(ByteBuffer context);

  private static native int updateNative(
      ByteBuffer context, byte[] out, byte[] in, int inLen, int encrypt, int inputOffset);

  private static native int doFinalNative(ByteBuffer context, byte[] out, int encrypt, int outputOffset);

  private static native int setGCMTagNative(ByteBuffer context, byte[] tag);
  private static native int getGCMTagNative(ByteBuffer context, byte[] tag);

  private static native int GCMIVToCTRIVNative(byte[] key, byte[] iv, byte[] ivOut, long libCtxInt);

  /**
   * Get the list of digest algorithms supported by the OpenSSL library.
   *
   * @return a Set of supported message digest algorithms.
   */
  protected static Set<String> getCipherList() {
    String[] messageDigestAlgorithms = listCiphers();
    return new HashSet<>(Arrays.asList(messageDigestAlgorithms));
  }

  /**
   * A native message digest context where the state of the current calculation is stored. Allocated
   * with {@linkplain #nativeContext()}, freed by the {@linkplain PhantomReferenceCleanup} with
   * {@linkplain #free(ByteBuffer)}.
   */
  private final ByteBuffer context;

  /** The OpenSSL algorithm name as returned by {@linkplain #listCiphers()}. */
  private final String algorithmBaseName;

  private String algoMode = "", algoPadding = "";
  private int blockSize = -1;
  private int OperationMode = 0;
  private long libCtx = 0;
  private boolean isFinal = false;
  private long bytesIn = 0;
  private long bytesOut = 0;

  private AlgorithmParameters algoParams;

  OpenSSLCipherNative(String openSslName) {
    try {
      NativeLoader.loadAll();
      algorithmBaseName = Objects.requireNonNull(openSslName);
      context = nativeContext();
      PhantomReferenceCleanup.enqueueForCleanup(this, OpenSSLCipherNative::free, context);
      libCtx = OpenSSLCryptoNative.getLibInstance();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private String getAlgorithimString() {
    if(algorithmBaseName ==  "AES-256-GCM") {
      return "id-aes256-GCM";
    } else if (this.algoMode.length() > 0 && !this.algorithmBaseName.contains(this.algoMode)) {
      if (this.algorithmBaseName.endsWith("-")) {
        return this.algorithmBaseName + this.algoMode;
      } else {
        return this.algorithmBaseName + "-" + this.algoMode;
      }
    }

    return algorithmBaseName;
  }

  /**
   * Free the native context that came from {@linkplain #nativeContext()}.
   *
   * @param context the context allocated with {@linkplain #nativeContext()}.
   */
  protected static void free(ByteBuffer context) {
    Objects.requireNonNull(context);
    if (!context.isDirect()) {
      throw new IllegalStateException("Illegal buffer passed in");
    }
    removeContext(context);
  }

  public void setOpenSSLLibCtx(long opensslLibCtx) {
    libCtx = opensslLibCtx;
  }

  @Override
  protected void engineSetMode(String mode) throws NoSuchAlgorithmException {
    algoMode = mode;
  }

  @Override
  protected void engineSetPadding(String padding) throws NoSuchPaddingException {
    if (padding.equals("NoPadding")) {
      algoPadding = "";
      return;
    }
    algoPadding = padding;
  }

  @Override
  protected int engineGetBlockSize() {
    if (this.blockSize < 0) {
      if(getAlgorithimString().toLowerCase().contains("aes")) {
        this.blockSize = 16; // AES is special: OpenSSL returns a block size of 1 but we need it to be 16.
      }else {
        this.blockSize =
        getBlockSizeNative(
            context,
            getAlgorithimString(),
            libCtx); // Cache the block size. It will remain constant for this cipher.
      }

    }
    return this.blockSize;
  }

  private boolean isAES_GCM() {
    return getAlgorithimString().toLowerCase().contains("aes") && 
    getAlgorithimString().toLowerCase().contains("gcm");
  }

  private boolean hasPadding() {
    return this.algoPadding.trim().length() > 0 && !this.algoPadding.contains("NoPadding");
  }

  @Override
  protected int engineGetOutputSize(int inputLen) {
    // Based on details from https://www.openssl.org/docs/man3.1/man3/EVP_EncryptUpdate.html
    if(getAlgorithimString().toLowerCase().contains("aes")) {
      if(getAlgorithimString().toLowerCase().contains("gcm")) {
        if(this.OperationMode == 1) {
          return inputLen+16;
        }else {
          return Math.max(0, inputLen-16);
        }
      }
      //Most AES ciphers have a block size of 1, however we set it to 16 for our usages. To handle padding in AES/CBC/PKCS5Padding
      //we need custom rules. 
      if(!(this.algoMode.toUpperCase().equals("CBC") && this.hasPadding())) {
        return inputLen;
      }else if(this.hasPadding() && this.isFinal) {
        int blockSize = engineGetBlockSize();

        if(this.OperationMode == Cipher.ENCRYPT_MODE) {
          if(((this.bytesIn + inputLen) % blockSize) == 0) {
            return inputLen;
          }else {
            return inputLen + blockSize; //add a full block to the end for padding
          }
        }else {
          int bytesOwed = 0;
          if(bytesIn > bytesOut) {
            bytesOwed += (bytesIn - bytesOut);
          }

          if(((this.bytesIn + inputLen) % blockSize) != 0){
            bytesOwed += blockSize;
          }

          return inputLen + bytesOwed;
        }

      }
    }
    int blockSize = engineGetBlockSize();

    int fullBlocks = inputLen / blockSize;

    if((inputLen % blockSize) > 0) {
      fullBlocks++;
    }else if(this.algoMode.toUpperCase().contains("ECB") && this.isFinal) {
      if(this.OperationMode == Cipher.ENCRYPT_MODE) {
        fullBlocks++; //add an extra block for encryption
      }else if(fullBlocks > 0) {
        fullBlocks--; //remove block decryption if possible, we can not have negitive blocks.
      }
    }

    int baseOutputSize = fullBlocks * blockSize;

    return baseOutputSize;
  }

  @Override
  protected byte[] engineGetIV() {
    byte[] temp = getOriginalIVNative(context);

    if(temp == null) {
      temp = new byte[0];
    }

    return temp;
  }

  @Override
  protected AlgorithmParameters engineGetParameters() {
    return algoParams;
  }

  @Override
  protected void engineInit(final int opmode, Key key, SecureRandom random)
      throws InvalidKeyException {
    this.blockSize = -1; // Reset block size on engine init.
    OperationMode = opmode;
    byte[] iv = new byte[engineGetBlockSize()];
    random.nextBytes(iv);
    int res =
        engineInitNative(
            context,
            getAlgorithimString(),
            key.getEncoded(),
            iv,
            OperationMode == Cipher.ENCRYPT_MODE ? 1 : 0,
            libCtx,
            this.hasPadding() ? 1 : 0);

    if (res == 0) {
      throw new InvalidKeyException("set cipher returned 0");
    }
  }

  @Override
  protected void engineInit(
      final int opmode, Key key, AlgorithmParameterSpec params, SecureRandom random)
      throws InvalidKeyException, InvalidAlgorithmParameterException {
    this.blockSize = -1; // Reset block size on engine init.
    OperationMode = opmode;
    byte[] iv = new byte[engineGetBlockSize()];

    if(params instanceof GCMParameterSpec) {
      iv = ((GCMParameterSpec) params).getIV();
    }else if (params instanceof IvParameterSpec){
      iv = ((IvParameterSpec) params).getIV();
    } else {
      random.nextBytes(iv); // Genereate iv if none is provided or throw an error?
    }
    
    int res =
        engineInitNative(
            context,
            getAlgorithimString(),
            key.getEncoded(),
            iv,
            OperationMode == Cipher.ENCRYPT_MODE ? 1 : 0,
            libCtx,
            this.hasPadding() ? 1 : 0);

    if (res == 0) {
      throw new InvalidKeyException("set cipher returned 0");
    }
  }

  @Override
  protected void engineInit(
      final int opmode, Key key, AlgorithmParameters params, SecureRandom random)
      throws InvalidKeyException, InvalidAlgorithmParameterException {
    this.blockSize = -1; // Reset block size on engine init.
    OperationMode = opmode;
    byte[] iv;
    try {
      iv = params.getParameterSpec(IvParameterSpec.class).getIV();
    } catch (Exception ex) {
      try {
        iv = params.getParameterSpec(GCMParameterSpec.class).getIV();
      }catch(Exception ex2) {
        iv = new byte[engineGetBlockSize()];
        random.nextBytes(
            iv); // Fallback and generate our own IV if there is an error getting the supplied IV.
      }
    }

    algoParams = params;

    int res =
        engineInitNative(
            context,
            getAlgorithimString(),
            key.getEncoded(),
            iv,
            OperationMode == Cipher.ENCRYPT_MODE ? 1 : 0,
            libCtx,
            this.hasPadding() ? 1 : 0);

    if (res == 0) {
      throw new InvalidKeyException("set cipher returned 0");
    }
  }

  @Override
  protected byte[] engineUpdate(byte[] input, int inputOffset, int inputLen) {
    int outputLen = engineGetOutputSize(inputLen);

    if(isAES_GCM() && !isFinal) {
      if(OperationMode != Cipher.ENCRYPT_MODE) {
        outputLen += 16;
      }
    }

    byte[] tempOutput = new byte[outputLen];

    int encrypt = OperationMode == Cipher.ENCRYPT_MODE ? 1 : 0;

    int res = updateNative(context, tempOutput, input, inputLen, encrypt, inputOffset);
    this.bytesOut += res;

    if (res < 0) {
      return null;
    } else if (res == outputLen) {
      this.bytesIn += inputLen;
      return tempOutput;
    }
    this.bytesIn += inputLen;


    byte[] output = new byte[res];

    System.arraycopy(
        tempOutput,
        0,
        output,
        0,
        res); // Shrink array. Result can be up to output len size, so it could be smaller.

    return output;
  }

  @Override
  protected int engineUpdate(
      byte[] input, int inputOffset, int inputLen, byte[] output, int outputOffset)
      throws ShortBufferException {
    int outputLen = output.length - outputOffset;

    int expectedOutputLen = engineGetOutputSize(inputLen);

    if(isAES_GCM() && !isFinal) {
      if(OperationMode != Cipher.ENCRYPT_MODE) {
        expectedOutputLen += 16;
      }
    }

    if (outputLen < expectedOutputLen) {
      throw new ShortBufferException();
    }

    byte[] tempOutput = new byte[outputLen];

    int encrypt = OperationMode == Cipher.ENCRYPT_MODE ? 1 : 0;

    int res = updateNative(context, tempOutput, input, inputLen, encrypt, inputOffset);
    this.bytesOut += res;

    if (res < 0) {
      return res; // Or Throw Exception? not sure or maybe return 0?
    }

    for (int i = 0, j = outputOffset; i < res && j < output.length; i++, j++) {
      output[j] = tempOutput[i];
    }

    this.bytesIn += inputLen;

    return res;
  }

  @Override
  protected byte[] engineDoFinal(byte[] input, int inputOffset, int inputLen)
      throws IllegalBlockSizeException, BadPaddingException {

    this.isFinal = true;
    int outputSize = engineGetOutputSize(inputLen);

    byte[] tmpOutput = new byte[outputSize];

    int size = 0;

    try {
      size = engineDoFinal(input, inputOffset, inputLen, tmpOutput, 0);
    } catch (ShortBufferException ex) {
      // Pass exception back to caller.
      throw new RuntimeException(ex);
    }

    if (size < 0) {
      return null;
    } else if (size == outputSize) {
      return tmpOutput;
    } else if (size == 0) {
      return new byte[0];
    }

    byte[] output = new byte[size];

    System.arraycopy(tmpOutput, 0, output, 0, size);

    return output;
  }

  @Override
  protected int engineDoFinal(
      byte[] input, int inputOffset, int inputLen, byte[] output, int outputOffset)
      throws ShortBufferException, IllegalBlockSizeException, BadPaddingException {
    
    this.isFinal = true;
    int totalLen = 0;
    int encrypt = (OperationMode == Cipher.ENCRYPT_MODE ? 1 : 0);
    byte[] tag = new byte[0];

    if(encrypt == 0 && getAlgorithimString().toLowerCase().contains("gcm")) {
      // Pull the last 16 bytes off the input to use as the tag if decrypting.
      if(inputLen < 16) {
        throw new ShortBufferException("GCM mode requires the last 16 bytes to be the GCM tag");
      }
      tag = Arrays.copyOfRange(input, inputLen - 16, inputLen);
      inputLen -= 16;
    }else if (getAlgorithimString().toLowerCase().contains("gcm")) {
      if(output.length - outputOffset < engineGetOutputSize(inputLen)) {
        throw new ShortBufferException("GCM mode requires an extra 16 bytes for the GCM tag");
      }
    }

    if (inputLen > 0) {
      int byteCount = engineUpdate(input, inputOffset, inputLen, output, outputOffset);
      if (byteCount == 0) {
        return 0;
      }
      outputOffset += byteCount;
      totalLen += byteCount;
    }

    if(encrypt == 0 && getAlgorithimString().toLowerCase().contains("gcm")) {
      // Must set the "tag" grabbed earlier before do final is called when decrypting.
      setGCMTagNative(context, tag);
    }

    int len = doFinalNative(context, output, encrypt, outputOffset);

    if(encrypt == 1 && getAlgorithimString().toLowerCase().contains("gcm")) {
      // In this case we must get the tag and append it to the output.
      tag = new byte[16];

      getGCMTagNative(context, tag);

      if(totalLen + len + 16 > output.length) {
        throw new RuntimeException("Unable to append GCM tag to encrypted data. Output buffer was not large enough");
      }else {
        for(int i = totalLen + len, j = 0; j < 16; i++, j++, len++) {
          output[i] = tag[j];
        } 
      }
    }

    return totalLen + len;
  }
}

// Read into AEAD INTERFACE. See https://www.openssl.org/docs/man3.1/man3/EVP_EncryptInit.html for more info about AES GCM.
