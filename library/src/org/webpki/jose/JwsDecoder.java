/*
 *  Copyright 2006-2019 WebPKI.org (http://webpki.org).
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
package org.webpki.jose;

import java.io.IOException;

import java.security.GeneralSecurityException;
import java.security.PublicKey;

import java.security.cert.X509Certificate;

import org.webpki.crypto.AlgorithmPreferences;
import org.webpki.crypto.AsymSignatureAlgorithms;
import org.webpki.crypto.MACAlgorithms;
import org.webpki.crypto.SignatureAlgorithms;

import org.webpki.json.JSONObjectReader;
import org.webpki.json.JSONParser;

import org.webpki.util.Base64URL;

public class JwsDecoder {
    
    String jwsProtectedHeaderB64U;
    
    JSONObjectReader jwsProtectedHeader;
    
    String optionalJwsPayloadB64U;
    
    SignatureAlgorithms signatureAlgorithm;
    
    byte[] signature;
    
    PublicKey optionalPublicKey;
    
    X509Certificate[] optionalCertificatePath;
    
    String optionalKeyId;
    
    public JwsDecoder(String jwsString) throws IOException, GeneralSecurityException {

        // Extract the JWS elements
        int endOfHeader = jwsString.indexOf('.');
        int startOfSignature = jwsString.lastIndexOf('.');
        if (endOfHeader == startOfSignature - 1)
        if (endOfHeader < 10 ||  startOfSignature > jwsString.length() - 10) {
            throw new GeneralSecurityException("JWS syntax error");
        }
        if (endOfHeader < startOfSignature - 1) {
            // In-line signature
            optionalJwsPayloadB64U = jwsString.substring(endOfHeader + 1, startOfSignature);
            Base64URL.decode(optionalJwsPayloadB64U);  // Syntax check
        }
        
        // Begin decoding the JWS header
        jwsProtectedHeaderB64U = jwsString.substring(0, endOfHeader);
        jwsProtectedHeader = JSONParser.parse(Base64URL.decode(jwsProtectedHeaderB64U));
        String algorithmParam = jwsProtectedHeader.getString(JOSESupport.ALG_JSON);
        
        // Get the binary signature
        signature = Base64URL.decode(jwsString.substring(startOfSignature + 1));

        // This is pretty ugly, two different conventions in the same standard!
        if (algorithmParam.equals(JOSESupport.EdDSA)) {
            signatureAlgorithm = signature.length == 64 ? 
                        AsymSignatureAlgorithms.ED25519 : AsymSignatureAlgorithms.ED448;
        } else if (algorithmParam.startsWith("HS")) {
            signatureAlgorithm = 
                    MACAlgorithms.getAlgorithmFromId(algorithmParam,
                                                     AlgorithmPreferences.JOSE);
        } else {
            signatureAlgorithm =
                    AsymSignatureAlgorithms.getAlgorithmFromId(algorithmParam, 
                                                               AlgorithmPreferences.JOSE);
        }
        
        // We don't bother about any other header data than possible public key
        // elements modulo JKU and X5U
        if (jwsProtectedHeader.hasProperty(JOSESupport.JWK_JSON)) {
            optionalPublicKey = JOSESupport.getPublicKey(jwsProtectedHeader);
        }
        if (jwsProtectedHeader.hasProperty(JOSESupport.X5C_JSON)) {
            if (optionalPublicKey != null) {
                throw new GeneralSecurityException("Both X5C and JWK?");
            }
            optionalCertificatePath = JOSESupport.getCertificatePath(jwsProtectedHeader);
            optionalPublicKey = optionalCertificatePath[0].getPublicKey();
        }
        if (signatureAlgorithm.isSymmetric() && optionalPublicKey != null) {
            throw new GeneralSecurityException("Mix of symmetric and asymmetric elements?");
        }
        optionalKeyId = jwsProtectedHeader.getStringConditional(JOSESupport.KID_JSON);
    }

    public JSONObjectReader getJwsProtectedHeader() {
        return jwsProtectedHeader;
    }

    public SignatureAlgorithms getSignatureAlgorithm() {
        return signatureAlgorithm;
    }

    public PublicKey getOptionalPublicKey() {
        return optionalPublicKey;
    }

    public X509Certificate[] getOptionalCertificatePath() {
        return optionalCertificatePath;
    }

    public String getOptionalKeyId() {
        return optionalKeyId;
    }
}
