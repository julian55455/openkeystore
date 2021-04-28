/*
 *  Copyright 2006-2020 WebPKI.org (http://webpki.org).
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

import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;

import org.webpki.util.ArrayUtil;

/**
 * Base class for holding CBOR maps.
 */
abstract class CBORMapBase extends CBORObject {

    private static final long serialVersionUID = 1L;
    
    private static boolean rfc7049Sorting = true;

    /**
     * Set RFC7049 key sorting.
     * Default: true
     * @param flag true for RFC7049, false for RFC 8949
     */
    static public void setRfc7049SortingMode(boolean flag) {
        rfc7049Sorting = flag;
    }

    class CBORKeyComparer implements Comparator<CBORObject> {

        @Override
        public int compare(CBORObject o1, CBORObject o2) {
            try {
                byte[] key1 = o1.encode();
                byte[] key2 = o2.encode();
                if (rfc7049Sorting && key1.length != key2.length) {
                    return key1.length - key2.length;
                }
                int minIndex = Math.min(key1.length, key2.length);
                for (int i = 0; i < minIndex; i++) {
                    int diff = (key1[i] & 0xff) - (key2[i] & 0xff);
                    if (diff != 0) {
                        return diff;
                    }
                }
                return key1.length - key2.length;
            } catch (IOException e) {
                 throw new RuntimeException(e);
            }
  //          return o1.toString().compareTo(o2.toString());
        }
    }

    Map<CBORObject, CBORObject> keys = new TreeMap<>(new CBORKeyComparer());

    CBORMapBase() {
    }

    void setObject(CBORObject key, CBORObject value) throws IOException {
        if (keys.put(key, value) != null) {
            throw new IOException("Duplicate key: " + key.toString());
        }
    }

    CBORObject getObject(CBORObject key) throws IOException {
        CBORObject cborObject = keys.get(key);
        if (cborObject == null) {
            throw new IOException("No such key: " + key.toString());
        }
        return cborObject;
    }

    @Override
    public CBORTypes getType() {
        return CBORTypes.INTEGER_MAP;
    }
 
    @Override
    public byte[] encode() throws IOException {
        byte[] encoded = getEncodedCodedValue(MT_MAP, keys.size(), false, false);
        for (CBORObject key : keys.keySet()) {
            encoded = ArrayUtil.add(encoded,
                                    ArrayUtil.add(key.encode(), 
                                                  keys.get(key).encode()));
        }
        return encoded;
    }
    
    @Override
    void internalToString(CBORObject initiator) {
        StringBuilder prettyPrintCopy = initiator.prettyPrint;
        prettyPrintCopy.append("{\n");
        initiator.indentationLevel++;
        boolean notFirst = false;
        for (CBORObject key : keys.keySet()) {
            CBORObject member = keys.get(key);
            if (notFirst) {
                prettyPrintCopy.insert(prettyPrintCopy.length() - 1, ',');
            }
            notFirst = true;
            initiator.indent();
            key.internalToString(initiator);
            prettyPrintCopy.append(": ");
            member.internalToString(initiator);
            prettyPrintCopy.append('\n');
        }
        initiator.indentationLevel--;
        initiator.indent();
        prettyPrintCopy.append('}');
    }
}
