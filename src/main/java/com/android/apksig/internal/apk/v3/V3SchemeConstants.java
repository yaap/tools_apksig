/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.apksig.internal.apk.v3;

import com.android.apksig.internal.util.AndroidSdkVersion;

/** Constants used by the V3 Signature Scheme signing and verification. */
public class V3SchemeConstants {
    private V3SchemeConstants() {}

    public static final int APK_SIGNATURE_SCHEME_V3_BLOCK_ID = 0xf05368c0;
    public static final int APK_SIGNATURE_SCHEME_V31_BLOCK_ID = 0x1b93ad61;
    public static final int APK_SIGNATURE_SCHEME_V32_BLOCK_ID = 0x70e1c89f;
    public static final int PROOF_OF_ROTATION_ATTR_ID = 0x3ba06f8c;

    public static final int MIN_SDK_WITH_V3_SUPPORT = AndroidSdkVersion.P;
    public static final int MIN_SDK_WITH_V31_SUPPORT = AndroidSdkVersion.T;
    public static final int MIN_SDK_WITH_V32_SUPPORT = AndroidSdkVersion.C;

    /**
     * By default, APK signing key rotation will target T, but packages that have previously
     * rotated can continue rotating on pre-T by specifying an SDK version <= 32 as the
     * --rotation-min-sdk-version parameter when using apksigner or when invoking
     * {@link com.android.apksig.ApkSigner.Builder#setMinSdkVersionForRotation(int)}.
     */
    public static final int DEFAULT_ROTATION_MIN_SDK_VERSION  = AndroidSdkVersion.T;

    /**
     * By default, the hybrid signing config will target Android C, but it is possible that
     * subsequent PQC signature algorithms will need to target a later release. Even after this
     * value is set, the signer engine should verify / update the value to match the first SDK
     * version that added support for the PQC signature algorithm.
     */
    public static final int DEFAULT_HYBRID_CONFIG_MIN_SDK_VERSION = AndroidSdkVersion.C;

    /**
     * This attribute is intended to be written to the V3.0 signer block as an additional attribute
     * whose value is the minimum SDK version supported for rotation by the V3.1 signing block. If
     * this value is set to X and a v3.1 signing block does not exist, or the minimum SDK version
     * for rotation in the v3.1 signing block is not X, then the APK should be rejected.
     */
    public static final int ROTATION_MIN_SDK_VERSION_ATTR_ID = 0x559f8b02;

    /**
     * This attribute is intended to be written to the V3.0 / V3.1 signer blocks as an additional
     * attribute whose value is the minimum SDK version supported by the hybrid signing block. If
     * this value is set to X and a v3.2 signing block does not exist, or the minimum SDK version
     * supported by the hybrid signing block is not X, then the APK should be rejected.
     */
    public static final int HYBRID_MIN_SDK_VERSION_ATTR_ID = 0xbf940529;

    /**
     * This attribute is written to a signer's block as an additional attribute to signify that the
     * signer is targeting a development release. This is required to support testing new signature
     * scheme versions or algorithms on new development releases as the previous platform release
     * SDK version is used as the development release SDK version until the development release SDK
     * is finalized.
     *
     * <p>Note: This was previously referenced as the rotation-min-sdk-version attribute, but it can
     * apply to any signer targeting a development release and is not limited to just rotation.
     */
    public static final int SIGNER_TARGETS_DEV_RELEASE_ATTR_ID = 0xc2a6b3ba;

    /**
     * This attribute is written to the V3.1 signer block as an additional attribute to signify that
     * the rotation-min-sdk-version is targeting a development release. This is required to support
     * testing rotation on new development releases as the previous platform release SDK version is
     * used as the development release SDK version until the development release SDK is finalized.
     *
     * @deprecated use {@link #SIGNER_TARGETS_DEV_RELEASE_ATTR_ID} instead.
     */
    public static final int ROTATION_ON_DEV_RELEASE_ATTR_ID = SIGNER_TARGETS_DEV_RELEASE_ATTR_ID;

    /**
     * The current development release; rotation / signing configs targeting this release should
     * be written with the {@link #PROD_RELEASE} SDK version and the dev release attribute.
     */
    public static final int DEV_RELEASE = AndroidSdkVersion.C;

    /**
     * The current production release.
     */
    public static final int PROD_RELEASE = AndroidSdkVersion.B;
}
