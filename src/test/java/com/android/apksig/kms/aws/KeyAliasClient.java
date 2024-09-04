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

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.AlgorithmSpec;
import software.amazon.awssdk.services.kms.model.AliasListEntry;
import software.amazon.awssdk.services.kms.model.CreateAliasRequest;
import software.amazon.awssdk.services.kms.model.CreateKeyRequest;
import software.amazon.awssdk.services.kms.model.DescribeKeyRequest;
import software.amazon.awssdk.services.kms.model.ExpirationModelType;
import software.amazon.awssdk.services.kms.model.GetParametersForImportRequest;
import software.amazon.awssdk.services.kms.model.GetParametersForImportResponse;
import software.amazon.awssdk.services.kms.model.ImportKeyMaterialRequest;
import software.amazon.awssdk.services.kms.model.ImportKeyMaterialResponse;
import software.amazon.awssdk.services.kms.model.KeyMetadata;
import software.amazon.awssdk.services.kms.model.KeySpec;
import software.amazon.awssdk.services.kms.model.KeyUsageType;
import software.amazon.awssdk.services.kms.model.ListAliasesRequest;
import software.amazon.awssdk.services.kms.model.ListAliasesResponse;
import software.amazon.awssdk.services.kms.model.NotFoundException;
import software.amazon.awssdk.services.kms.model.OriginType;
import software.amazon.awssdk.services.kms.model.WrappingKeySpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class KeyAliasClient implements AutoCloseable {
    private static final String ALIAS_PREFIX = "alias/";
    private final KmsClient mClient;

    public KeyAliasClient() {
        mClient = KmsClient.builder().httpClientBuilder(UrlConnectionHttpClient.builder()).build();
    }

    Optional<KeyMetadata> getKeyForAlias(String keyAlias) {
        try {
            return Optional.of(
                    mClient.describeKey(
                                    DescribeKeyRequest.builder()
                                            .keyId(ALIAS_PREFIX + keyAlias)
                                            .build())
                            .keyMetadata());
        } catch (NotFoundException _unused) {
            System.out.println("Requested key alias " + keyAlias + "was not found!");
            return Optional.empty();
        }
    }

    /** List all key aliases (for test accounts only - pages through all aliases). */
    public List<AliasListEntry> listKeyAliases() {
        ListAliasesResponse response = mClient.listAliases();
        List<AliasListEntry> aliases = new ArrayList<>(response.aliases());
        while (response.truncated()) {
            response =
                    mClient.listAliases(
                            ListAliasesRequest.builder().marker(response.nextMarker()).build());
            aliases.addAll(response.aliases());
        }
        return aliases;
    }

    Optional<AliasListEntry> findKeyAlias(String keyAlias) {
        return listKeyAliases().stream()
                .filter(as -> as.aliasName().equals(ALIAS_PREFIX + keyAlias))
                .findFirst();
    }

    KeyMetadata createKey(String keyAlias, KeySpec keySpec) {
        KeyMetadata createdKey =
                mClient.createKey(
                                CreateKeyRequest.builder()
                                        .keyUsage(KeyUsageType.SIGN_VERIFY)
                                        .keySpec(keySpec)
                                        .build())
                        .keyMetadata();

        mClient.createAlias(
                CreateAliasRequest.builder()
                        .aliasName(keyAlias)
                        .targetKeyId(createdKey.keyId())
                        .build());

        return createdKey;
    }

    KeyMetadata createKeyForImport(String keyAlias, KeySpec keySpec) {
        KeyMetadata createdKey =
                mClient.createKey(
                                CreateKeyRequest.builder()
                                        .keyUsage(KeyUsageType.SIGN_VERIFY)
                                        .keySpec(keySpec)
                                        .origin(OriginType.EXTERNAL)
                                        .build())
                        .keyMetadata();

        mClient.createAlias(
                CreateAliasRequest.builder()
                        .aliasName(ALIAS_PREFIX + keyAlias)
                        .targetKeyId(createdKey.keyId())
                        .build());

        return createdKey;
    }

    GetParametersForImportResponse getParametersForImport(
            WrappingKeySpec wrappingKeySpec, AlgorithmSpec wrappingAlgorithm, String keyId) {
        return mClient.getParametersForImport(
                GetParametersForImportRequest.builder()
                        .wrappingKeySpec(wrappingKeySpec)
                        .wrappingAlgorithm(wrappingAlgorithm)
                        .keyId(keyId)
                        .build());
    }

    ImportKeyMaterialResponse importKeyMaterial(
            String keyId, SdkBytes importToken, byte[] wrappedKey) {
        return mClient.importKeyMaterial(
                ImportKeyMaterialRequest.builder()
                        .keyId(keyId)
                        .expirationModel(ExpirationModelType.KEY_MATERIAL_DOES_NOT_EXPIRE)
                        .importToken(importToken)
                        .encryptedKeyMaterial(SdkBytes.fromByteArray(wrappedKey))
                        .build());
    }

    @Override
    public void close() throws Exception {
        mClient.close();
    }
}
