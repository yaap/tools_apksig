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

import com.google.api.gax.paging.AbstractPagedListResponse;
import com.google.cloud.kms.v1.CreateCryptoKeyRequest;
import com.google.cloud.kms.v1.CreateKeyRingRequest;
import com.google.cloud.kms.v1.CryptoKey;
import com.google.cloud.kms.v1.CryptoKeyName;
import com.google.cloud.kms.v1.CryptoKeyVersion;
import com.google.cloud.kms.v1.CryptoKeyVersionName;
import com.google.cloud.kms.v1.CryptoKeyVersionTemplate;
import com.google.cloud.kms.v1.ImportCryptoKeyVersionRequest;
import com.google.cloud.kms.v1.ImportJob;
import com.google.cloud.kms.v1.KeyManagementServiceClient;
import com.google.cloud.kms.v1.KeyRing;
import com.google.cloud.kms.v1.KeyRingName;
import com.google.cloud.kms.v1.LocationName;
import com.google.cloud.kms.v1.ProtectionLevel;
import com.google.protobuf.ByteString;

import java.util.Optional;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/** GCP client convenience wrapper for interacting with a key ring. */
public class KeyRingClient implements AutoCloseable {
    final KeyRingName mKeyRingName;
    final KeyManagementServiceClient mClient;

    public KeyRingClient(KeyRingName keyRingName) throws Exception {
        this.mKeyRingName = keyRingName;
        this.mClient = KeyManagementServiceClient.create();
    }

    /**
     * Create the key ring corresponding to this client's KeyRingName.
     *
     * <p>Should only be run ONCE.
     */
    KeyRing createKeyRing() {
        return mClient.createKeyRing(
                CreateKeyRingRequest.newBuilder()
                        .setParent(
                                LocationName.of(
                                                mKeyRingName.getProject(),
                                                mKeyRingName.getLocation())
                                        .toString())
                        .setKeyRingId(mKeyRingName.getKeyRing())
                        .build());
    }

    public KeyRing getKeyRing() {
        return mClient.getKeyRing(mKeyRingName);
    }

    /** Create a regular GCP KMS key (non import). */
    CryptoKey createCryptoKey(String cryptoKeyId) {
        return mClient.createCryptoKey(mKeyRingName, cryptoKeyId, CryptoKey.getDefaultInstance());
    }

    /** Find by name. */
    Optional<CryptoKey> findCryptoKey(String cryptoKeyId) {
        String cryptoKeyName =
                CryptoKeyName.of(
                                mKeyRingName.getProject(),
                                mKeyRingName.getLocation(),
                                mKeyRingName.getKeyRing(),
                                cryptoKeyId)
                        .toString();
        return stream(mClient.listCryptoKeys(mKeyRingName))
                .filter(cryptoKey -> cryptoKey.getName().equals(cryptoKeyName))
                .findFirst();
    }

    /** Find the version 1 of a crypto key by name. */
    Optional<CryptoKeyVersion> findCryptoKeyVersion(String cryptoKeyId) {
        return findCryptoKeyVersion(cryptoKeyId, "1");
    }

    /** Find a specific crypto key version. */
    Optional<CryptoKeyVersion> findCryptoKeyVersion(String cryptoKeyId, String versionId) {
        String cryptoKeyVersionName =
                CryptoKeyVersionName.of(
                                mKeyRingName.getProject(),
                                mKeyRingName.getLocation(),
                                mKeyRingName.getKeyRing(),
                                cryptoKeyId,
                                versionId)
                        .toString();
        return findCryptoKey(cryptoKeyId)
                .flatMap(
                        k ->
                                stream(mClient.listCryptoKeyVersions(k.getName()))
                                        .filter(ckv -> ckv.getName().equals(cryptoKeyVersionName))
                                        .findFirst());
    }

    /** Import a local private key to GCP KMS. */
    CryptoKeyVersion importCryptoKey(
            CryptoKey cryptoKey, ImportJob importJob, byte[] wrappedKeyMaterial) throws Exception {
        return mClient.importCryptoKeyVersion(
                ImportCryptoKeyVersionRequest.newBuilder()
                        .setParent(cryptoKey.getName())
                        .setImportJob(importJob.getName())
                        .setAlgorithm(
                                CryptoKeyVersion.CryptoKeyVersionAlgorithm
                                        .RSA_SIGN_PKCS1_2048_SHA256)
                        .setRsaAesWrappedKey(ByteString.copyFrom(wrappedKeyMaterial))
                        .build());
    }

    CryptoKey createCryptoKeyForImport(
            String cryptoKeyId,
            ProtectionLevel protectionLevel,
            CryptoKeyVersion.CryptoKeyVersionAlgorithm algorithm) {
        return mClient.createCryptoKey(
                CreateCryptoKeyRequest.newBuilder()
                        .setParent(mKeyRingName.toString())
                        .setCryptoKeyId(cryptoKeyId)
                        .setCryptoKey(
                                CryptoKey.newBuilder()
                                        .setPurpose(CryptoKey.CryptoKeyPurpose.ASYMMETRIC_SIGN)
                                        .setVersionTemplate(
                                                CryptoKeyVersionTemplate.newBuilder()
                                                        .setProtectionLevel(protectionLevel)
                                                        .setAlgorithm(algorithm)
                                                        .build())
                                        .setImportOnly(true))
                        .setSkipInitialVersionCreation(true)
                        .build());
    }

    ImportJob createImportJob(String cryptoKeyId) {
        ImportJob importJob =
                mClient.createImportJob(
                        mKeyRingName,
                        cryptoKeyId,
                        ImportJob.newBuilder()
                                .setProtectionLevel(ProtectionLevel.SOFTWARE)
                                .setImportMethod(ImportJob.ImportMethod.RSA_OAEP_3072_SHA1_AES_256)
                                .build());

        while (mClient.getImportJob(importJob.getName()).getState()
                != ImportJob.ImportJobState.ACTIVE) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        return importJob;
    }

    /** Utility to turn paged responses into streams. */
    private static <T> Stream<T> stream(AbstractPagedListResponse<?, ?, T, ?, ?> response) {
        return StreamSupport.stream(response.iterateAll().spliterator(), false);
    }

    @Override
    public void close() throws Exception {
        this.mClient.close();
    }
}
