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

import static com.android.apksig.kms.KmsType.AWS;

import com.android.apksig.SignerEngine;
import com.android.apksig.kms.KmsException;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.SignRequest;
import software.amazon.awssdk.services.kms.model.SigningAlgorithmSpec;

public class AwsSignerEngine implements SignerEngine {
    private final String mKeyAlias;
    private static final String ALIAS_PREFIX = "alias/";
    private final SigningAlgorithmSpec mSigningAlgorithmSpec;

    public AwsSignerEngine(String keyAlias, String jcaSignatureAlgorithm) {
        mKeyAlias = keyAlias;
        mSigningAlgorithmSpec = fromJcaSignatureAlgorithm(jcaSignatureAlgorithm);
    }

    @Override
    public byte[] sign(byte[] data) {
        try (KmsClient client =
                KmsClient.builder().httpClientBuilder(UrlConnectionHttpClient.builder()).build()) {
            return client.sign(
                            SignRequest.builder()
                                    .keyId(ALIAS_PREFIX + mKeyAlias)
                                    .signingAlgorithm(mSigningAlgorithmSpec)
                                    .message(SdkBytes.fromByteArray(data))
                                    .build())
                    .signature()
                    .asByteArray();
        }
    }

    private static SigningAlgorithmSpec fromJcaSignatureAlgorithm(String jcaSignatureAlgorithm) {
        switch (jcaSignatureAlgorithm) {
            case "SHA256withRSA/PSS":
                return SigningAlgorithmSpec.RSASSA_PSS_SHA_256;
            case "SHA512withRSA/PSS":
                return SigningAlgorithmSpec.RSASSA_PSS_SHA_512;
            case "SHA256withRSA":
                return SigningAlgorithmSpec.RSASSA_PKCS1_V1_5_SHA_256;
            case "SHA512withRSA":
                return SigningAlgorithmSpec.RSASSA_PKCS1_V1_5_SHA_512;
            case "SHA256withECDSA":
                return SigningAlgorithmSpec.ECDSA_SHA_256;
            case "SHA512withECDSA":
                return SigningAlgorithmSpec.ECDSA_SHA_512;
            default:
                throw new KmsException(
                        AWS, "Signature algorithm " + jcaSignatureAlgorithm + " not supported");
        }
    }
}
