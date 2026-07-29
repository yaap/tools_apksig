/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.apksig;

import static com.android.apksig.internal.util.Resources.getDefaultSignerConfigFromResources;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Utility class containing methods that can be used to verify the results of an APK signature
 * verification.
 */
public class ApkSigTestUtils {
    /**
     * Asserts the provided verification {@code result} contains the expected {@code signers} for
     * each scheme that was used to verify the APK's signature.
     */
    static void assertResultContainsSigners(ApkVerifier.Result result, String... signers)
            throws Exception {
        assertResultContainsSigners(result, false, signers);
    }

    /**
     * Asserts the provided verification {@code result} contains the expected {@code signers} for
     * each scheme that was used to verify the APK's signature; if {@code rotationExpected} is set
     * to {@code true}, then the first element in {@code signers} is treated as the expected
     * original signer for any V1, V2, and V3 (where applicable) signatures, and the last element is
     * the rotated expected signer for V3+.
     */
    static void assertResultContainsSigners(
            ApkVerifier.Result result, boolean rotationExpected, String... signers)
            throws Exception {
        // A result must be successfully verified before verifying any of the result's signers.
        assertTrue(result.isVerified());

        List<X509Certificate> expectedSigners = new ArrayList<>();
        for (String signer : signers) {
            ApkSigner.SignerConfig signerConfig = getDefaultSignerConfigFromResources(signer);
            expectedSigners.addAll(signerConfig.getCertificates());
        }
        // If rotation is expected then the V1 and V2 signature should only be signed by the
        // original signer.
        List<X509Certificate> expectedV1Signers =
                rotationExpected ? List.of(expectedSigners.get(0)) : expectedSigners;
        List<X509Certificate> expectedV2Signers =
                rotationExpected ? List.of(expectedSigners.get(0)) : expectedSigners;
        // V3 only supports a single signer; if rotation is not expected or the V3.1 block contains
        // the rotated signing key then the expected V3.0 signer should be the original signer.
        List<X509Certificate> expectedV3Signers =
                !rotationExpected || result.isVerifiedUsingV31Scheme()
                        ? List.of(expectedSigners.get(0))
                        : List.of(expectedSigners.get(expectedSigners.size() - 1));

        if (result.isVerifiedUsingV1Scheme()) {
            Set<X509Certificate> v1Signers = new HashSet<>();
            for (ApkVerifier.Result.V1SchemeSignerInfo signer : result.getV1SchemeSigners()) {
                v1Signers.add(signer.getCertificate());
            }
            assertTrue(
                    "Expected V1 signers: "
                            + getAllSubjectNamesFrom(expectedV1Signers)
                            + ", actual V1 signers: "
                            + getAllSubjectNamesFrom(v1Signers),
                    v1Signers.containsAll(expectedV1Signers));
        }

        if (result.isVerifiedUsingV2Scheme()) {
            Set<X509Certificate> v2Signers = new HashSet<>();
            for (ApkVerifier.Result.V2SchemeSignerInfo signer : result.getV2SchemeSigners()) {
                v2Signers.add(signer.getCertificate());
            }
            assertTrue(
                    "Expected V2 signers: "
                            + getAllSubjectNamesFrom(expectedV2Signers)
                            + ", actual V2 signers: "
                            + getAllSubjectNamesFrom(v2Signers),
                    v2Signers.containsAll(expectedV2Signers));
        }

        if (result.isVerifiedUsingV3Scheme()) {
            Set<X509Certificate> v3Signers = new HashSet<>();
            for (ApkVerifier.Result.V3SchemeSignerInfo signer : result.getV3SchemeSigners()) {
                v3Signers.add(signer.getCertificate());
            }
            assertTrue(
                    "Expected V3 signers: "
                            + getAllSubjectNamesFrom(expectedV3Signers)
                            + ", actual V3 signers: "
                            + getAllSubjectNamesFrom(v3Signers),
                    v3Signers.containsAll(expectedV3Signers));
        }

        if (result.isVerifiedUsingV31Scheme()) {
            Set<X509Certificate> v31Signers = new HashSet<>();
            for (ApkVerifier.Result.V3SchemeSignerInfo signer : result.getV31SchemeSigners()) {
                v31Signers.add(signer.getCertificate());
            }
            // V3.1 only supports specifying signatures with a rotated signing key; if a V3.1
            // signing block was verified then ensure it contains the expected rotated signer.
            List<X509Certificate> expectedV31Signers =
                    List.of(expectedSigners.get(expectedSigners.size() - 1));
            assertTrue(
                    "Expected V3.1 signers: "
                            + getAllSubjectNamesFrom(expectedV31Signers)
                            + ", actual V3.1 signers: "
                            + getAllSubjectNamesFrom(v31Signers),
                    v31Signers.containsAll(expectedV31Signers));
        }
    }

    /**
     * Asserts the provided verification {@code result} contains the expected V3.2 {@code signers}.
     */
    public static void assertResultContainsV32Signers(ApkVerifier.Result result, String... signers)
            throws Exception {
        assertResultContainsV32SignersTargetingSdkVersion(result, 0, signers);
    }

    /**
     * Asserts the provided verification {@code result} contains the expected V3.2 {@code signers}
     * targeting the provided {@code sdkVersion}.
     *
     * <p>If the {@code sdkVersion} is 0, then the SDK targeting check will be skipped.
     */
    public static void assertResultContainsV32SignersTargetingSdkVersion(
            ApkVerifier.Result result, int sdkVersion, String... signers) throws Exception {
        assertTrue(result.isVerified());
        assertTrue(result.isVerifiedUsingV32Scheme());
        List<X509Certificate> expectedSigners = new ArrayList<>();
        for (String signer : signers) {
            ApkSigner.SignerConfig signerConfig = getDefaultSignerConfigFromResources(signer);
            expectedSigners.addAll(signerConfig.getCertificates());
        }
        ApkVerifier.Result.V32SchemeSignerInfo v32Signer = result.getV32SchemeSigner();
        Set<X509Certificate> v32Signers = new HashSet<>();
        v32Signers.add(v32Signer.getClassicalSignerInfo().getCertificate());
        v32Signers.add(v32Signer.getPqcSignerInfo().getCertificate());
        assertTrue(
                "Expected V3.2 signers: "
                        + getAllSubjectNamesFrom(expectedSigners)
                        + ", actual V3.2 signers: "
                        + getAllSubjectNamesFrom(v32Signers),
                v32Signers.containsAll(expectedSigners));
        if (sdkVersion > 0) {
            assertEquals(
                    "Expected V3.2 classical signer to target SDK version " + sdkVersion,
                    sdkVersion,
                    v32Signer.getClassicalSignerInfo().getMinSdkVersion());
            assertEquals(
                    "Expected V3.2 PQC signer to target SDK version " + sdkVersion,
                    sdkVersion,
                    v32Signer.getPqcSignerInfo().getMinSdkVersion());
        }
    }

    /**
     * Returns a comma delimited {@code String} containing all of the Subject Names from the
     * provided {@code certificates}.
     */
    public static String getAllSubjectNamesFrom(Collection<X509Certificate> certificates) {
        StringBuilder result = new StringBuilder();
        for (X509Certificate certificate : certificates) {
            if (result.length() > 0) {
                result.append(", ");
            }
            result.append(certificate.getSubjectDN().getName());
        }
        return result.toString();
    }

    /**
     * Asserts that the provided {@code result} from an APK signature verification was successfully
     * verified.
     *
     * <p>If any errors were encountered during signature verification, either general APK errors or
     * those from individual signers, this method will display the output with the test result.
     */
    public static void assertVerified(ApkVerifier.Result result) {
        assertVerified(result, "APK");
    }

    /**
     * Asserts that the provided {@code result} from the signature verification of the APK with ID
     * {@code apkId} was successfully verified.
     *
     * <p>If any failures were encountered during signature verification, either general APK errors
     * or those from individual signers, this method will display the output with the test result.
     */
    public static void assertVerified(ApkVerifier.Result result, String apkId) {
        if (result.isVerified()) {
            return;
        }

        StringBuilder msg = new StringBuilder();
        for (ApkVerifier.IssueWithParams issue : result.getErrors()) {
            appendIssueToMessage(issue, msg);
        }
        for (ApkVerifier.Result.V1SchemeSignerInfo signer : result.getV1SchemeSigners()) {
            String signerName = "JAR Signer " + signer.getName() + ": ";
            for (ApkVerifier.IssueWithParams issue : signer.getErrors()) {
                appendIssueToMessageForSigner(issue, msg, signerName);
            }
        }
        for (ApkVerifier.Result.V2SchemeSignerInfo signer : result.getV2SchemeSigners()) {
            String signerName = "APK Signature Scheme v2 signer #" + (signer.getIndex() + 1) + ": ";
            for (ApkVerifier.IssueWithParams issue : signer.getErrors()) {
                appendIssueToMessageForSigner(issue, msg, signerName);
            }
        }
        for (ApkVerifier.Result.V3SchemeSignerInfo signer : result.getV3SchemeSigners()) {
            String signerName = "APK Signature Scheme v3 signer #" + (signer.getIndex() + 1) + ": ";
            for (ApkVerifier.IssueWithParams issue : signer.getErrors()) {
                appendIssueToMessageForSigner(issue, msg, signerName);
            }
        }
        for (ApkVerifier.Result.V3SchemeSignerInfo signer : result.getV31SchemeSigners()) {
            String signerName =
                    "APK Signature Scheme v3.1 signer #" + (signer.getIndex() + 1) + ": ";
            for (ApkVerifier.IssueWithParams issue : signer.getErrors()) {
                appendIssueToMessageForSigner(issue, msg, signerName);
            }
        }

        ApkVerifier.Result.V32SchemeSignerInfo v32Signer = result.getV32SchemeSigner();
        if (v32Signer != null) {
            ApkVerifier.Result.V3SchemeSignerInfo classicalSigner =
                    v32Signer.getClassicalSignerInfo();
            if (classicalSigner != null) {
                String signerName = "APK Signature Scheme v3.2 classical signer: ";
                for (ApkVerifier.IssueWithParams issue : classicalSigner.getErrors()) {
                    appendIssueToMessageForSigner(issue, msg, signerName);
                }
            }
            ApkVerifier.Result.V3SchemeSignerInfo pqcSigner = v32Signer.getPqcSignerInfo();
            if (pqcSigner != null) {
                String signerName = "APK Signature Scheme v3.2 PQC signer: ";
                for (ApkVerifier.IssueWithParams issue : pqcSigner.getErrors()) {
                    appendIssueToMessageForSigner(issue, msg, signerName);
                }
            }
        }

        fail(apkId + " did not verify: " + msg);
    }

    /**
     * Appends the provided general {@code issue} to the {@code msg} that will be included with the
     * test result.
     */
    private static void appendIssueToMessage(ApkVerifier.IssueWithParams issue, StringBuilder msg) {
        appendIssueToMessageForSigner(issue, msg, null);
    }

    /**
     * Appends the provided {@code issue} for the corresponding {@code signerName} to the {@code
     * msg} that will be included with the test result.
     */
    private static void appendIssueToMessageForSigner(
            ApkVerifier.IssueWithParams issue, StringBuilder msg, String signerName) {
        if (msg.length() > 0) {
            msg.append('\n');
        }
        if (signerName != null) {
            msg.append(signerName);
        }
        msg.append(issue.getIssue()).append(": ").append(issue);
    }
}
