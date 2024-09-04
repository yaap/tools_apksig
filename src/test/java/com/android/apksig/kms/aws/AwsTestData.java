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

package com.android.apksig.kms.aws;

import static com.android.apksig.internal.util.Resources.FIRST_RSA_2048_SIGNER_RESOURCE_NAME;
import static com.android.apksig.internal.util.Resources.SECOND_RSA_2048_SIGNER_RESOURCE_NAME;
import static com.android.apksig.kms.KeyWrapper.wrapKeyForImport;

import software.amazon.awssdk.services.kms.model.AlgorithmSpec;
import software.amazon.awssdk.services.kms.model.GetParametersForImportResponse;
import software.amazon.awssdk.services.kms.model.KeyMetadata;
import software.amazon.awssdk.services.kms.model.KeySpec;
import software.amazon.awssdk.services.kms.model.WrappingKeySpec;

/** Supplies data for tests involving AWS KMS */
public class AwsTestData {
    /** Creates the test data. This should be run ONCE. */
    public static void main(String[] args) throws Exception {
        importRsa2048Sha256(FIRST_RSA_2048_SIGNER_RESOURCE_NAME);
        importRsa2048Sha256(SECOND_RSA_2048_SIGNER_RESOURCE_NAME);
    }

    private static void importRsa2048Sha256(String privateKeyNameInResources) throws Exception {
        try (KeyAliasClient keyAliasClient = new KeyAliasClient()) {
            KeyMetadata keyMetadata =
                    keyAliasClient
                            .getKeyForAlias(privateKeyNameInResources)
                            .orElseGet(
                                    () ->
                                            keyAliasClient.createKeyForImport(
                                                    privateKeyNameInResources, KeySpec.RSA_2048));

            GetParametersForImportResponse importParameters =
                    keyAliasClient.getParametersForImport(
                            WrappingKeySpec.RSA_4096,
                            AlgorithmSpec.RSA_AES_KEY_WRAP_SHA_1,
                            keyMetadata.keyId());

            byte[] wrappedKey =
                    wrapKeyForImport(
                            privateKeyNameInResources, importParameters.publicKey().asByteArray());

            keyAliasClient.importKeyMaterial(
                    keyMetadata.keyId(), importParameters.importToken(), wrappedKey);
        }
    }
}
