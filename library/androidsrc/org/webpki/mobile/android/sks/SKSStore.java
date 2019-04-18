/*
 *  Copyright 2006-2016 WebPKI.org (http://webpki.org).
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
package org.webpki.mobile.android.sks;

import android.util.Log;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import java.math.BigInteger;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;

import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;

import java.util.Date;
import java.util.HashSet;

import org.webpki.sks.SKSException;

import android.content.Context;

import android.provider.Settings;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import javax.security.auth.x500.X500Principal;

public abstract class SKSStore {

    private static final String PERSISTENCE_SKS   = "SKS";  // SKS persistence file

    private static final String DEVICE_KEY_NAME   = "device";

    private static final String LOG_NAME          = "ANDROID/KS";

    private static final String ANDROID_KEYSTORE  = "AndroidKeyStore";    // Hardware backed keys


    private static AndroidSKSImplementation sks;

    private static HashSet<String> supportedAlgorithms;

    private static KeyStore hardwareBacked;

    static {
        try {
            hardwareBacked = KeyStore.getInstance(ANDROID_KEYSTORE);
            hardwareBacked.load(null);
        } catch (Exception e) {
            Log.e(LOG_NAME, e.getMessage());
            throw new RuntimeException();
        }
    }

    private static X509Certificate deviceCertificate;
    private static PrivateKey deviceKey;

    static void getDeviceCredentials(String androidId) {
        if (deviceCertificate == null) {
            try {
                if (hardwareBacked.isKeyEntry(DEVICE_KEY_NAME)) {
                    deviceKey = (PrivateKey) hardwareBacked.getKey(DEVICE_KEY_NAME, null);
                    Log.i(LOG_NAME, "Had a key already");
                } else {
                    byte[] serial = new byte[8];
                    new SecureRandom().nextBytes(serial);
                    KeyPairGenerator kpg = KeyPairGenerator.getInstance(
                            KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE);
                    kpg.initialize(new KeyGenParameterSpec.Builder(
                            DEVICE_KEY_NAME, KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                            .setAlgorithmParameterSpec(new ECGenParameterSpec("secp256r1"))
                            .setDigests(KeyProperties.DIGEST_SHA256)
                            .setCertificateSerialNumber(new BigInteger(1, serial))
                            .setCertificateNotBefore(new Date(System.currentTimeMillis() - 600000L))
                            .setCertificateSubject(new X500Principal("serialNumber=" +
                                    (androidId == null ? "N/A" : androidId) + ",CN=Android SKS"))
                            .build());
                    KeyPair keyPair = kpg.generateKeyPair();
                    deviceKey = keyPair.getPrivate();
                    Log.i(LOG_NAME, "Created a key");
                }
                deviceCertificate = (X509Certificate) hardwareBacked.getCertificate(DEVICE_KEY_NAME);
            } catch (Exception e) {
                Log.e(LOG_NAME, e.getMessage());
            }
        }
    }

    public static synchronized AndroidSKSImplementation createSKS(String callerForLog,
                                                                  Context caller,
                                                                  boolean saveIfNew) {
        getDeviceCredentials(Settings.Secure.getString(caller.getContentResolver(),
                             Settings.Secure.ANDROID_ID));
        if (sks == null) {
            try {
                sks = (AndroidSKSImplementation) new ObjectInputStream(
                        caller.openFileInput(PERSISTENCE_SKS)).readObject();
                getAlgorithms();
                Log.i(callerForLog, "SKS found, restoring it");
            } catch (Exception e) {
                Log.i(callerForLog, "SKS not found, recreating it");
                try {
                    sks = new AndroidSKSImplementation();
                    if (saveIfNew) {
                        serializeSKS(callerForLog, caller);
                    }
                    getAlgorithms();
                } catch (Exception e2) {
                    Log.e(callerForLog, e2.getMessage());
                }
            }
            sks.setDeviceCredentials(new X509Certificate[]{deviceCertificate}, deviceKey);
        }
        return sks;
    }

    private static void getAlgorithms() throws SKSException {
        supportedAlgorithms = new HashSet<String>();
        for (String alg : sks.getDeviceInfo().getSupportedAlgorithms()) {
            supportedAlgorithms.add(alg);
        }
    }

    public static synchronized void serializeSKS(String callerForLog, Context caller) {
        if (sks != null) {
            try {
                ObjectOutputStream oos = new ObjectOutputStream(caller.openFileOutput(PERSISTENCE_SKS, Context.MODE_PRIVATE));
                oos.writeObject(sks);
                oos.close();
                Log.i(callerForLog, "Successfully wrote SKS");
            } catch (Exception e) {
                Log.e(callerForLog, "Couldn't write SKS: " + e.getMessage());
            }
        }
    }

    public static boolean isSupported(String algorithm) {
        return supportedAlgorithms.contains(algorithm);
    }
}