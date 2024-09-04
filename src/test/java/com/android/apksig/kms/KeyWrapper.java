/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.apksig.kms;

import com.android.apksig.ApkSignerTest;
import com.android.apksig.internal.util.Resources;

import com.google.crypto.tink.subtle.Kwp;
import com.google.protobuf.ByteString;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;

/** Utility class to wrap private keys for cloud import */
public class KeyWrapper {
    private KeyWrapper() {}

    /**
     * Inspired by <a
     * href="https://cloud.google.com/kms/docs/importing-a-key#kms-import-manually-wrapped-key-java">GCP
     * Docs</a>.
     *
     * @param keyNameInResources a private key from test resources
     * @param wrappingPublicKeyBytes the DER encoded public key bytes, from a public key provided by
     *     the cloud provider
     * @return the wrapped key for upload
     */
    public static byte[] wrapKeyForImport(String keyNameInResources, byte[] wrappingPublicKeyBytes)
            throws Exception {
        byte[] privateKeyBytes =
                Resources.toByteArray(ApkSignerTest.class, keyNameInResources + ".pk8");

        // Generate a temporary 32-byte key for AES-KWP and wrap the key material.
        byte[] kwpKey = new byte[32];
        new SecureRandom().nextBytes(kwpKey);
        Kwp kwp = new Kwp(kwpKey);
        final byte[] wrappedTargetKey = kwp.wrap(privateKeyBytes);

        PublicKey publicKey =
                KeyFactory.getInstance("RSA")
                        .generatePublic(new X509EncodedKeySpec(wrappingPublicKeyBytes));

        // Wrap the KWP key using the import job key.
        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-1AndMGF1Padding");
        cipher.init(
                Cipher.ENCRYPT_MODE,
                publicKey,
                new OAEPParameterSpec(
                        "SHA-1", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT));
        final byte[] wrappedWrappingKey = cipher.doFinal(kwpKey);

        // Concatenate the wrapped KWP key and the wrapped target key.
        return ByteString.copyFrom(wrappedWrappingKey)
                .concat(ByteString.copyFrom(wrappedTargetKey))
                .toByteArray();
    }
}
