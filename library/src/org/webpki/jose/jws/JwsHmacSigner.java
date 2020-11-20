/*
 *  Copyright 2018-2020 WebPKI.org (http://webpki.org).
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
package org.webpki.jose.jws;

import java.io.IOException;

import java.security.GeneralSecurityException;

import org.webpki.crypto.MACAlgorithms;

/**
 * JWS HMAC signer
 */
public class JwsHmacSigner extends JwsSigner {
    
    MACAlgorithms macAlgorithm;
    byte[] secretKey;
    
    /**
     * Initialize signer.
     * 
     * Note that a signer object may be used any number of times
     * (assuming that the same parameters are valid).  It is also
     * thread-safe.
     * @param secretKey The key to use
     * @param macAlgorithm HMAC Algorithm to use
     * @throws IOException 
     */
    public JwsHmacSigner(byte[] secretKey, MACAlgorithms macAlgorithm) throws IOException {
        super(macAlgorithm);
        this.secretKey = secretKey;
        this.macAlgorithm = macAlgorithm;
    }

    @Override
    byte[] signObject(byte[] dataToBeSigned) throws IOException, GeneralSecurityException {
        return macAlgorithm.digest(secretKey, dataToBeSigned);
    }
}
