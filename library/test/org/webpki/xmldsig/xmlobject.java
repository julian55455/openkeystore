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
package org.webpki.xmldsig;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.PublicKey;

import java.security.PrivateKey;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import org.webpki.util.ArrayUtil;
import org.webpki.util.Base64;

import org.webpki.xml.XMLObjectWrapper;
import org.webpki.xml.XMLSchemaCache;
import org.webpki.xml.DOMReaderHelper;
import org.webpki.xml.DOMWriterHelper;
import org.webpki.xml.DOMAttributeReaderHelper;

import org.webpki.crypto.DemoKeyStore;
import org.webpki.crypto.HmacAlgorithms;
import org.webpki.crypto.KeyAlgorithms;
import org.webpki.crypto.KeyStoreSigner;
import org.webpki.crypto.KeyStoreVerifier;
import org.webpki.crypto.SignatureWrapper;
import org.webpki.crypto.AsymSignatureAlgorithms;
import org.webpki.crypto.AsymKeySignerInterface;
import org.webpki.crypto.HmacSignerInterface;
import org.webpki.crypto.HmacVerifierInterface;

public class xmlobject extends XMLObjectWrapper implements XMLEnvelopedInput {
    static byte[] symkey;

    static {
        try {
            symkey = new Base64().getBase64BinaryFromUnicode("sBeVJTrHwIETmlgRlvswfSjnYD34V2PdiEQadrnG8ko=");
        } catch (Exception e) {
        }
    }

    static class rsaKey implements AsymKeySignerInterface {
        PrivateKey priv_key;
        AsymSignatureAlgorithms algorithm;

        rsaKey(PrivateKey priv_key) throws IOException {
            this.priv_key = priv_key;
            algorithm = KeyAlgorithms.getKeyAlgorithm(priv_key).getRecommendedSignatureAlgorithm();
        }

        @Override
        public byte[] signData(byte[] data) throws IOException, GeneralSecurityException {
                return new SignatureWrapper(algorithm, priv_key)
                        .update(data)
                        .sign();
        }

        @Override
        public AsymSignatureAlgorithms getAlgorithm()
                throws IOException, GeneralSecurityException {
            // TODO Auto-generated method stub
            return null;
        }

    }

    public String id;

    Element sig;
    Element ins;

    public String value;

    XMLSignatureWrapper signature;


    public Document getEnvelopeRoot() throws IOException {
        return getRootDocument();
    }

    public String getReferenceURI() throws IOException {
        return id;
    }

    public Element getTargetElem() throws IOException {
        return sig;
    }

    public Element getInsertElem() throws IOException {
        return ins;
    }

    public XMLSignatureWrapper getSignature() throws IOException {
        return signature;
    }

    public void init() throws IOException {
        addSchema("xmlobject.xsd");
    }


    protected boolean hasQualifiedElements() {
        return true;
    }


    public String namespace() {
        return "http://example.com/xml";
    }


    public String element() {
        return "Outer";
    }


    protected void fromXML(DOMReaderHelper rd) throws IOException, GeneralSecurityException {
        DOMAttributeReaderHelper ah = rd.getAttributeHelper();

        rd.getChild();

        sig = rd.getNext("Sig");
        id = ah.getString("ID");
        rd.getChild();
        value = rd.getString("Inner");
        ins = rd.getNext("InsertHere");
        rd.getChild();
        signature = (XMLSignatureWrapper) wrap(rd.getNext());
    }


    protected void toXML(DOMWriterHelper wr) throws IOException {
        wr.initializeRootObject(null);

        sig = wr.addChildElement("Sig");
        wr.setStringAttribute("ID", id);

        wr.addString("Inner", value);
        ins = wr.addChildElement("InsertHere");

        wr.getParent();

        wr.getParent();

    }


    public static void main(String args[]) throws Exception {
        if (args.length != 3 ||
                !(args[0].equals("-rsa") || args[0].equals("-x509") || args[0].equals("-sym")) ||
                !(args[1].equals("-sign") || args[1].equals("-verify"))) {
            System.out.println("xmlobject -(rsa|x509|sym) -(sign|verify) xmlfile");
            System.exit(3);
        }
        if (args[1].equals("-sign")) {
            xmlobject o = new xmlobject();
            o.id = "id.1234";
            o.value = "Some text";
            o.forcedDOMRewrite();
            if (args[0].equals("-rsa")) {
                PrivateKey privateKey = (PrivateKey) DemoKeyStore.getMarionKeyStore().getKey("mykey", DemoKeyStore.getSignerPassword().toCharArray());
                PublicKey publicKey = DemoKeyStore.getMarionKeyStore().getCertificate("mykey").getPublicKey();
                XMLAsymKeySigner xmls = new XMLAsymKeySigner(new rsaKey(privateKey), publicKey);
                xmls.createEnvelopedSignature(o);
            } else if (args[0].equals("-x509")) {
                KeyStoreSigner signer = new KeyStoreSigner(DemoKeyStore.getMarionKeyStore(), null);
                signer.setKey(null, DemoKeyStore.getSignerPassword());
                XMLSigner xmls = new XMLSigner(signer);
                xmls.createEnvelopedSignature(o);
            } else {
                XMLSymKeySigner xmls = new XMLSymKeySigner(new HmacSignerInterface() {

                    public HmacAlgorithms getAlgorithm() throws IOException {
                        return HmacAlgorithms.HMAC_SHA256;
                    }

                    public byte[] signData(byte[] data) throws IOException, GeneralSecurityException {
                        return getAlgorithm().digest(symkey, data);
                    }

                });
                xmls.createEnvelopedSignature(o);
            }
            ArrayUtil.writeFile(args[2], o.writeXML());
        } else {
            XMLSchemaCache xml = new XMLSchemaCache();
            xml.addWrapper(XMLSignatureWrapper.class);
            xml.addWrapper(xmlobject.class);
            xmlobject o = (xmlobject) xml.parse(ArrayUtil.readFile(args[2]));
            if (args[0].equals("-rsa")) {
                XMLAsymKeyVerifier verifier = new XMLAsymKeyVerifier();
                verifier.validateEnvelopedSignature(o);
                if (!ArrayUtil.compare(verifier.getPublicKey().getEncoded(),
                        DemoKeyStore.getMarionKeyStore().getCertificate("mykey").getPublicKey().getEncoded())) {
                    throw new Exception("Bad public key");
                }
            } else if (args[0].equals("-x509")) {
                XMLVerifier verifier = new XMLVerifier(new KeyStoreVerifier(DemoKeyStore.getMarionKeyStore()));
                verifier.validateEnvelopedSignature(o);
            } else {
                XMLSymKeyVerifier verifier = new XMLSymKeyVerifier(new HmacVerifierInterface() {
                    public boolean verifyData(byte[] data, byte[] digest, HmacAlgorithms algorithm, String keyId) 
                            throws IOException, GeneralSecurityException {
                        if (algorithm != HmacAlgorithms.HMAC_SHA256) {
                            throw new IOException("Bad sym ALG");
                        }
                        return ArrayUtil.compare(digest, HmacAlgorithms.HMAC_SHA256.digest(symkey, data));
                    }
                });
                verifier.validateEnvelopedSignature(o);
            }
        }
    }

}
