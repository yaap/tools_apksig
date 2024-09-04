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

import static com.android.apksig.kms.KmsType.GCP;

import com.android.apksig.SignerEngine;
import com.android.apksig.kms.KmsException;

import com.google.cloud.kms.v1.AsymmetricSignRequest;
import com.google.cloud.kms.v1.CryptoKeyVersionName;
import com.google.cloud.kms.v1.KeyManagementServiceClient;
import com.google.protobuf.ByteString;

import java.io.IOException;

/** Signs data using Google Cloud Platform. */
public class GcpSignerEngine implements SignerEngine {
    private final String mKeyAlias;

    /**
     * Create an engine to sign data with GCP
     *
     * @param keyAlias must be in the format of a parsable <a
     *     href="https://cloud.google.com/java/docs/reference/google-cloud-spanner/latest/com.google.spanner.admin.database.v1.CryptoKeyVersionName">CryptoKeyVersionName</a>
     */
    public GcpSignerEngine(String keyAlias) {
        mKeyAlias = keyAlias;
    }

    @Override
    public byte[] sign(byte[] data) {
        try (KeyManagementServiceClient client = KeyManagementServiceClient.create()) {
            CryptoKeyVersionName cryptoKeyVersionName = CryptoKeyVersionName.parse(mKeyAlias);
            return client.asymmetricSign(
                            AsymmetricSignRequest.newBuilder()
                                    .setName(cryptoKeyVersionName.toString())
                                    .setData(ByteString.copyFrom(data))
                                    .build())
                    .getSignature()
                    .toByteArray();
        } catch (IOException e) {
            throw new KmsException(GCP, "Error initializing KeyManagementServiceClient", e);
        }
    }
}
