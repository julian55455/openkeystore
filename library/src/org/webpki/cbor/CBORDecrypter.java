/*
 *  Copyright 2006-2021 WebPKI.org (http://webpki.org).
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package org.webpki.cbor;

import java.io.IOException;

import java.security.GeneralSecurityException;

import org.webpki.crypto.ContentEncryptionAlgorithms;
import org.webpki.crypto.EncryptionCore;

import org.webpki.cbor.CBORCryptoUtils.POLICY;
import org.webpki.cbor.CBORCryptoUtils.Collector;

import static org.webpki.cbor.CBORCryptoConstants.*;

/**
 * Base class for decrypting data.
 * <p>
 * See {@link CBOREncrypter} for details.
 * </p>
  * <p>
 * Note that decrypter objects may be used any number of times
 * (assuming that the same parameters are valid).  They are also
 * thread-safe.
 * </p>
 */
public abstract class CBORDecrypter {

    CBORDecrypter() {}
    
    abstract byte[] getContentEncryptionKey(CBORMap innerObject,
                                            ContentEncryptionAlgorithms contentEncryptionAlgorithm,
                                            CBORObject optionalKeyId) 
            throws IOException, GeneralSecurityException;

    POLICY customDataPolicy = POLICY.FORBIDDEN;
    Collector customDataCallBack;
    
    /**
     * Sets custom extension data policy.
     * <p>
     * By default custom data elements ({@link CBORCryptoConstants#CUSTOM_DATA_LABEL}) 
     * are rejected ({@link CBORCryptoUtils.POLICY#FORBIDDEN}).
     * </p>
     * <p>
     * See <a href='doc-files/crypto-options.html'>crypto options</a> for details.
     * </p>
     * @param customDataPolicy Define level of support
     * @param customDataCallBack Interface for reading custom data
     * @return <code>this</code>
     */
    public CBORDecrypter setCustomDataPolicy(POLICY customDataPolicy, 
                                             Collector customDataCallBack) {
        this.customDataPolicy = customDataPolicy;
        this.customDataCallBack = customDataCallBack;
        return this;
    }

    POLICY tagPolicy = POLICY.FORBIDDEN;
    Collector tagCallBack;

    /**
     * Sets tag wrapping policy.
     * <p>
     * By default wrapped containers are rejected ({@link CBORCryptoUtils.POLICY#FORBIDDEN}).
     * </p>
     * <p>
     * See <a href='doc-files/crypto-options.html'>crypto options</a> for details.
     * </p>
     * @param tagPolicy Define level of support
     * @param tagCallBack Interface for reading tag
     * @return <code>this</code>
     */
    public CBORDecrypter setTagPolicy(POLICY tagPolicy, Collector tagCallBack) {
        this.tagPolicy = tagPolicy;
        this.tagCallBack = tagCallBack;
        return this;
    }    
 
    /**
     * Decrypts data.
     * <p>
     * This method presumes that <code>encryptionObject</code> holds
     * an encryption object according to CEF.
     * </p>
     * 
     * @param encryptionObject CBOR encryption object
     * @return Decrypted data
     * @throws IOException
     * @throws GeneralSecurityException
     */
    public byte[] decrypt(CBORObject encryptionObject) throws IOException, 
                                                              GeneralSecurityException {

        // There may be a tag holding the encryption container object (main map).
        CBORMap cefContainer = CBORCryptoUtils.unwrapContainerMap(encryptionObject,
                                                                  tagPolicy,
                                                                  tagCallBack);

        // Get the mandatory content encryption algorithm.
        ContentEncryptionAlgorithms contentEncryptionAlgorithm =
                ContentEncryptionAlgorithms.getAlgorithmFromId(
                        cefContainer.getObject(ALGORITHM_LABEL).getInt());

        // Possible key encryption kicks in here.  That is, there is a sub map.
        CBORMap innerObject = this instanceof CBORSymKeyDecrypter ? 
                cefContainer : cefContainer.getObject(KEY_ENCRYPTION_LABEL).getMap();
              
        // Fetch optional keyId.
        CBORObject optionalKeyId = CBORCryptoUtils.getKeyId(innerObject);

        // Special handling of custom data.
        CBORCryptoUtils.getCustomData(cefContainer, customDataPolicy, customDataCallBack);

        // Get the content encryption key which also may be encrypted.
        byte[] contentDecryptionKey = getContentEncryptionKey(innerObject,
                                                              contentEncryptionAlgorithm,
                                                              optionalKeyId);
        
        // Read and remove the encryption object (map) parameters that
        // do not participate (because they cannot) in "authData".
        byte[] iv = cefContainer.readByteStringAndRemoveKey(IV_LABEL);
        byte[] tag = cefContainer.readByteStringAndRemoveKey(TAG_LABEL);
        byte[] cipherText = cefContainer.readByteStringAndRemoveKey(CIPHER_TEXT_LABEL);
        
        // Check that there is no unread (illegal) data like public 
        // keys in symmetric encryption or just plain unknown elements.
        cefContainer.checkForUnread();
        
        // Now we should have everything for decrypting the actual data.
        // Use the remaining CBOR data as "authData".
        
        // Note that the following operation depends on that the actual
        // CBOR implementation supports fully canonical (deterministic)
        // parsing and code generation! This implementation shows that
        // this is quite simple.
        byte[] authData = encryptionObject.encode();
        
        // Be nice and restore the object as well.
        cefContainer.setByteString(IV_LABEL, iv);
        cefContainer.setByteString(TAG_LABEL, tag);
        cefContainer.setByteString(CIPHER_TEXT_LABEL, cipherText);
         
        // Perform the actual decryption.
        return EncryptionCore.contentDecryption(contentEncryptionAlgorithm,
                                                contentDecryptionKey,
                                                cipherText, 
                                                iv, 
                                                authData,
                                                tag);
    }
}
