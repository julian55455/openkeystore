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

/**
 * Class for holding CBOR string maps.
 */
public class CBORStringMap extends CBORMapBase {

    private static final long serialVersionUID = 1L;

    CBORStringMap() {
    }

    public CBORStringMap setObject(String key, CBORObject value) throws IOException {
        setObject(new CBORString(key), value);
        return this;
    }

    public CBORObject getObject(String key) throws IOException {
        return getObject(new CBORString(key));
    }

    public int getInt32(String key) throws IOException {
        return getObject(key).getInt32();
    }

    public long getInt64(String key) throws IOException {
        return getObject(key).getInt64();
    }

    public byte[] getByteArray(String key) throws IOException {
        return getObject(key).getByteArray();
    }

    public CBORArray getArray(String key) throws IOException {
        return getObject(key).getArray();
    }

    public CBORIntegerMap getIntegerMap(String key) throws IOException {
        return getObject(key).getIntegerMap();
    }
 
    public CBORStringMap getStringMap(String key) throws IOException {
        return getObject(key).getStringMap();
    }
}
