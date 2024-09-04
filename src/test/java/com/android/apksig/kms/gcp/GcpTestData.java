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

package com.android.apksig.kms.gcp;

import static com.android.apksig.internal.util.Resources.FIRST_RSA_2048_SIGNER_RESOURCE_NAME;
import static com.android.apksig.internal.util.Resources.SECOND_RSA_2048_SIGNER_RESOURCE_NAME;
import static com.android.apksig.internal.util.Resources.THIRD_RSA_2048_SIGNER_RESOURCE_NAME;
import static com.android.apksig.kms.KeyWrapper.wrapKeyForImport;

import com.google.cloud.kms.v1.CryptoKey;
import com.google.cloud.kms.v1.CryptoKeyVersion;
import com.google.cloud.kms.v1.CryptoKeyVersionName;
import com.google.cloud.kms.v1.ImportJob;
import com.google.cloud.kms.v1.KeyRingName;
import com.google.cloud.kms.v1.ProtectionLevel;

import java.util.Base64;

/** Supplies data for tests involving GCP KMS */
public class GcpTestData {
    private static final KeyRingName KEY_RING_NAME =
            KeyRingName.of("apksigner-cloud-kms", "us-central1", "testV3");

    /** Finds the supplied key and returns its {@link CryptoKeyVersionName} */
    static CryptoKeyVersionName getCryptoKeyVersionName(String cryptoKeyId) throws Exception {
        try (KeyRingClient client = new KeyRingClient(KEY_RING_NAME)) {
            return client.findCryptoKeyVersion(cryptoKeyId)
                    .map(k -> CryptoKeyVersionName.parse(k.getName()))
                    .orElseThrow(() -> new RuntimeException(cryptoKeyId + " does not exist"));
        }
    }

    /** Creates the test data. This should be run ONCE. */
    public static void main(String[] args) throws Exception {
        try (KeyRingClient client = new KeyRingClient(KEY_RING_NAME)) {
            client.createKeyRing();
            importRsa2048Sha256(client, FIRST_RSA_2048_SIGNER_RESOURCE_NAME);
            importRsa2048Sha256(client, SECOND_RSA_2048_SIGNER_RESOURCE_NAME);
            importRsa2048Sha256(client, THIRD_RSA_2048_SIGNER_RESOURCE_NAME);
        }
    }

    private static void importRsa2048Sha256(KeyRingClient client, String privateKeyNameInResources)
            throws Exception {
        CryptoKey cryptoKey =
                client.createCryptoKeyForImport(
                        privateKeyNameInResources,
                        ProtectionLevel.SOFTWARE,
                        CryptoKeyVersion.CryptoKeyVersionAlgorithm.RSA_SIGN_PKCS1_2048_SHA256);
        ImportJob importJob = client.createImportJob(cryptoKey.getName());

        String publicKeyStr = importJob.getPublicKey().getPem();
        // Manually convert PEM to DER. :-(
        publicKeyStr = publicKeyStr.replace("-----BEGIN PUBLIC KEY-----", "");
        publicKeyStr = publicKeyStr.replace("-----END PUBLIC KEY-----", "");
        publicKeyStr = publicKeyStr.replaceAll("\n", "");
        byte[] publicKeyBytes = Base64.getDecoder().decode(publicKeyStr);

        byte[] wrappedKey = wrapKeyForImport(privateKeyNameInResources, publicKeyBytes);

        client.importCryptoKey(cryptoKey, importJob, wrappedKey);
    }
}
