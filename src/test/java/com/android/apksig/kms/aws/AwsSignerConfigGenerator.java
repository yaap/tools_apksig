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

import com.android.apksig.ApkSigner;
import com.android.apksig.ApkSignerTest;
import com.android.apksig.KeyConfig;
import com.android.apksig.SigningCertificateLineage;
import com.android.apksig.internal.util.Resources;
import com.android.apksig.kms.KmsType;

import java.security.cert.X509Certificate;
import java.util.List;

/** Generates {@link ApkSigner.SignerConfig}s for AWS signing in tests */
public class AwsSignerConfigGenerator {
    private AwsSignerConfigGenerator() {}

    /**
     * Generate a config where the signer config name, file name of cert in resources (sans the file
     * extension), and the name of the key alias in AWS are all the same.
     */
    public static ApkSigner.SignerConfig getSignerConfigFromResources(
            String name, boolean deterministicDsaSigning) throws Exception {
        return getSignerConfigFromResources(name, name, name, deterministicDsaSigning);
    }

    /** Generate a config for AWS given the key alias and resource names provided. */
    public static ApkSigner.SignerConfig getSignerConfigFromResources(
            String signerConfigName,
            String keyAliasName,
            String certNameInResources,
            boolean deterministicDsaSigning)
            throws Exception {
        List<X509Certificate> certs =
                Resources.toCertificateChain(
                        ApkSignerTest.class, certNameInResources + ".x509.pem");

        try (KeyAliasClient keyAliasClient = new KeyAliasClient()) {
            if (!keyAliasClient.findKeyAlias(keyAliasName).isPresent()) {
                throw new IllegalStateException("key alias " + keyAliasName + " does not exist");
            }

            return new ApkSigner.SignerConfig.Builder(
                            signerConfigName,
                            new KeyConfig.Kms(KmsType.AWS, keyAliasName),
                            certs,
                            deterministicDsaSigning)
                    .build();
        }
    }

    /** Generate a lineage SignerConfig given the AWS key alias and cert name provided */
    public static SigningCertificateLineage.SignerConfig getLineageSignerConfigFromResources(
            Class<?> cls, String keyAliasName, String certNameInResources) throws Exception {
        try (KeyAliasClient keyAliasClient = new KeyAliasClient()) {
            if (!keyAliasClient.findKeyAlias(keyAliasName).isPresent()) {
                throw new IllegalStateException("key alias " + keyAliasName + " does not exist");
            }

            X509Certificate cert = Resources.toCertificate(cls, certNameInResources + ".x509.pem");
            return new SigningCertificateLineage.SignerConfig.Builder(
                            new KeyConfig.Kms(KmsType.AWS, keyAliasName), cert)
                    .build();
        }
    }

    /**
     * Generate a lineage config where the file name of cert in resources (sans the file extension)
     * is the same as the name of the key alias in AWS.
     */
    public static SigningCertificateLineage.SignerConfig getLineageSignerConfigFromResources(
            Class<?> cls, String keyAliasNameAndCertNameInResources) throws Exception {
        return getLineageSignerConfigFromResources(
                cls, keyAliasNameAndCertNameInResources, keyAliasNameAndCertNameInResources);
    }
}
