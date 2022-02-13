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

import java.math.BigInteger;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;

import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;

import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.RSAPublicKeySpec;

import java.util.HashMap;

import org.webpki.crypto.KeyAlgorithms;
import org.webpki.crypto.KeyTypes;
import org.webpki.crypto.OkpSupport;

import static org.webpki.cbor.CBORCryptoConstants.*;

import org.webpki.util.ArrayUtil;

/**
 * Class for CBOR/COSE public keys.
 * 
 */
public class CBORPublicKey {
    
    private CBORPublicKey() {}
    
    static final HashMap<KeyAlgorithms, CBORInteger> WEBPKI_2_COSE_CRV = new HashMap<>();

    static {
        WEBPKI_2_COSE_CRV.put(KeyAlgorithms.NIST_P_256, COSE_CRV_NIST_P_256);
        WEBPKI_2_COSE_CRV.put(KeyAlgorithms.NIST_P_384, COSE_CRV_NIST_P_384);
        WEBPKI_2_COSE_CRV.put(KeyAlgorithms.NIST_P_521, COSE_CRV_NIST_P_521);
        WEBPKI_2_COSE_CRV.put(KeyAlgorithms.X25519,     COSE_CRV_X25519);
        WEBPKI_2_COSE_CRV.put(KeyAlgorithms.X448,       COSE_CRV_X448);
        WEBPKI_2_COSE_CRV.put(KeyAlgorithms.ED25519,    COSE_CRV_ED25519);
        WEBPKI_2_COSE_CRV.put(KeyAlgorithms.ED448,      COSE_CRV_ED448);
    }
    
    static final HashMap<Integer, KeyAlgorithms> COSE_2_WEBPKI_CRV = new HashMap<>();
    
    static {
        for (KeyAlgorithms key : WEBPKI_2_COSE_CRV.keySet()) {
            try {
                COSE_2_WEBPKI_CRV.put(WEBPKI_2_COSE_CRV.get(key).getInt(), key);
            } catch (IOException e) {
            }
        }
    }
    
    static final HashMap<Integer,KeyTypes> keyTypes = new HashMap<>();
    
    static {
        try {
            keyTypes.put(COSE_RSA_KTY.getInt(), KeyTypes.RSA);
            keyTypes.put(COSE_EC2_KTY.getInt(), KeyTypes.EC);
            keyTypes.put(COSE_OKP_KTY.getInt(), KeyTypes.EDDSA); // XEC and EDDSA share kty...
        } catch (IOException e) {
        }
    }

    static CBORByteString cryptoBinary(BigInteger value) {
        byte[] cryptoBinary = value.toByteArray();
        if (cryptoBinary[0] == 0x00) {
            byte[] woZero = new byte[cryptoBinary.length - 1];
            System.arraycopy(cryptoBinary, 1, woZero, 0, woZero.length);
            cryptoBinary = woZero;
        }
        return new CBORByteString(cryptoBinary);        
    }

    static CBORByteString curvePoint(BigInteger value, 
                                     KeyAlgorithms ec) throws GeneralSecurityException {
        byte[] curvePoint = value.toByteArray();
        if (curvePoint.length > (ec.getPublicKeySizeInBits() + 7) / 8) {
            if (curvePoint[0] != 0) {
                throw new GeneralSecurityException("Unexpected EC point");
            }
            return cryptoBinary(value);
        }
        while (curvePoint.length < (ec.getPublicKeySizeInBits() + 7) / 8) {
            curvePoint = ArrayUtil.add(new byte[]{0}, curvePoint);
        }
        return new CBORByteString(curvePoint);        
    }
    
     /**
     * Java/JCE to CBOR/COSE conversion.
     * 
     * @param publicKey
     * @return Public key in CBOR format
     * @throws IOException
     * @throws GeneralSecurityException
     */
    public static CBORMap encode(PublicKey publicKey) 
            throws IOException, GeneralSecurityException {
        CBORMap cborPublicKey = new CBORMap();
        KeyAlgorithms keyAlg = KeyAlgorithms.getKeyAlgorithm(publicKey);
        switch (keyAlg.getKeyType()) {
        case RSA:
            RSAPublicKey rsaPublicKey = (RSAPublicKey) publicKey;
            cborPublicKey.setObject(COSE_KTY_LABEL, COSE_RSA_KTY)
                         .setObject(COSE_RSA_N_LABEL, cryptoBinary(rsaPublicKey.getModulus()))
                         .setObject(COSE_RSA_E_LABEL, 
                                    cryptoBinary(rsaPublicKey.getPublicExponent()));
            break;

        case EC:
            ECPoint ecPoint = ((ECPublicKey) publicKey).getW();
            cborPublicKey.setObject(COSE_KTY_LABEL, COSE_EC2_KTY)
                         .setObject(COSE_EC2_CRV_LABEL, WEBPKI_2_COSE_CRV.get(keyAlg))
                         .setObject(COSE_EC2_X_LABEL, curvePoint(ecPoint.getAffineX(), keyAlg))
                         .setObject(COSE_EC2_Y_LABEL, curvePoint(ecPoint.getAffineY(), keyAlg));
            break;
 
        default:  // EDDSA and XEC
            cborPublicKey.setObject(COSE_KTY_LABEL, COSE_OKP_KTY)
                         .setObject(COSE_OKP_CRV_LABEL, WEBPKI_2_COSE_CRV.get(keyAlg))
                         .setObject(COSE_OKP_X_LABEL, new CBORByteString(
                                 OkpSupport.public2RawOkpKey(publicKey, keyAlg)));
        }
        return cborPublicKey;
    }

    static BigInteger getCryptoBinary(CBORObject value) 
            throws IOException, GeneralSecurityException {
        byte[] cryptoBinary = value.getByteString();
        if (cryptoBinary[0] == 0x00) {
            throw new GeneralSecurityException("RSA key parameter contains leading zeroes");
        }
        return new BigInteger(1, cryptoBinary);
    }

    static BigInteger getCurvePoint(CBORObject value, KeyAlgorithms ec) 
            throws IOException, GeneralSecurityException {
        byte[] fixedBinary = value.getByteString();
        if (fixedBinary.length != (ec.getPublicKeySizeInBits() + 7) / 8) {
            throw new GeneralSecurityException("Public EC key parameter is not normalized");
        }
        return new BigInteger(1, fixedBinary);
    }

    static KeyAlgorithms getKeyAlgorithm(CBORObject curve) throws IOException,
                                                                  GeneralSecurityException {
        KeyAlgorithms keyAlgorithm = COSE_2_WEBPKI_CRV.get(curve.getInt());
        if (keyAlgorithm == null) {
            throw new GeneralSecurityException("No such key/curve algorithm: " + curve.getInt());
        }
        return keyAlgorithm;
    }

    /**
     * CBOR/COSE to Java/JCE conversion.
     * 
     * @param cborPublicKey Public key in CBOR format
     * @return Public key as a Java object 
     * @throws IOException
     * @throws GeneralSecurityException
     */
    public static PublicKey decode(CBORObject cborPublicKey) 
    throws IOException, GeneralSecurityException {
        CBORMap publicKeyMap = cborPublicKey.getMap();
        int coseKty = publicKeyMap.getObject(COSE_KTY_LABEL).getInt();
        KeyTypes keyType = keyTypes.get(coseKty);
        if (keyType == null) {
            throw new GeneralSecurityException("Unrecognized key type: " + coseKty);
        }
        KeyAlgorithms keyAlgorithm;
        PublicKey publicKey;

        if (keyType == KeyTypes.RSA) {
            publicKey =  KeyFactory.getInstance("RSA").generatePublic(
                new RSAPublicKeySpec(getCryptoBinary(publicKeyMap.getObject(COSE_RSA_N_LABEL)),
                                     getCryptoBinary(publicKeyMap.getObject(COSE_RSA_E_LABEL))));

        } else if (keyType == KeyTypes.EC) {
            keyAlgorithm = getKeyAlgorithm(publicKeyMap.getObject(COSE_EC2_CRV_LABEL));
            if (keyAlgorithm.getKeyType() != KeyTypes.EC) {
                throw new GeneralSecurityException(keyAlgorithm.getKeyType()  +
                                                   " is not a valid EC curve");
            }
            publicKey = KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(
                new ECPoint(getCurvePoint(publicKeyMap.getObject(COSE_EC2_X_LABEL), keyAlgorithm),
                            getCurvePoint(publicKeyMap.getObject(COSE_EC2_Y_LABEL), keyAlgorithm)),
                keyAlgorithm.getECParameterSpec()));

        } else {
            keyAlgorithm = getKeyAlgorithm(publicKeyMap.getObject(COSE_OKP_CRV_LABEL));
            if (keyAlgorithm.getKeyType() != KeyTypes.EDDSA &&
                keyAlgorithm.getKeyType() != KeyTypes.XEC) {
                throw new GeneralSecurityException(keyAlgorithm.getKeyType()  +
                                                   " is not a valid OKP curve");
            }
            publicKey = OkpSupport.raw2PublicOkpKey(
                    publicKeyMap.getObject(COSE_OKP_X_LABEL).getByteString(), 
                    keyAlgorithm);
        }
        publicKeyMap.checkForUnread();
        return publicKey;
    }
}
