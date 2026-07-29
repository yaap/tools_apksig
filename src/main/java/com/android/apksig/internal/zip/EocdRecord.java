/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.apksig.internal.zip;

import static com.android.apksig.internal.zip.CentralDirectoryRecord.MIN_VERSION_SUPPORT_ZIP64;
import static com.android.apksig.internal.zip.ZipUtils.UINT16_MAX_VALUE;
import static com.android.apksig.internal.zip.ZipUtils.UINT32_MAX_VALUE;
import static com.android.apksig.internal.zip.ZipUtils.ZIP64_EOCD_LOCATOR_SIG;
import static com.android.apksig.internal.zip.ZipUtils.ZIP64_EOCD_REC_HEADER_SIZE;
import static com.android.apksig.internal.zip.ZipUtils.ZIP64_EOCD_REC_SIG;
import static com.android.apksig.internal.zip.ZipUtils.ZIP64_EOCD_SIZE_OFFSET;
import static com.android.apksig.internal.zip.ZipUtils.ZIP_EOCD_COMMENT_FIELD_OFFSET;
import static com.android.apksig.internal.zip.ZipUtils.ZIP_EOCD_COMMENT_LENGTH_FIELD_OFFSET;
import static com.android.apksig.internal.zip.ZipUtils.ZIP_EOCD_REC_MIN_SIZE;
import static com.android.apksig.internal.zip.ZipUtils.ZIP_EOCD_REC_SIG;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * ZIP End of Central Directory record.
 */
public class EocdRecord {
    public static final int CD_RECORD_COUNT_ON_DISK_OFFSET = 8;
    public static final int CD_RECORD_COUNT_TOTAL_OFFSET = 10;
    public static final int CD_SIZE_OFFSET = 12;
    public static final int CD_OFFSET_OFFSET = 16;
    public static final int ZIP64_EOCD_REC_MIN_SIZE = 56;
    public static final int ZIP64_EOCD_LOCATOR_LENGTH = 20;

    public static ByteBuffer createWithModifiedCentralDirectoryInfo(
            ByteBuffer original,
            int centralDirectoryRecordCount,
            long centralDirectorySizeBytes,
            long centralDirectoryOffset) {
        boolean needsZip64 =
                (centralDirectoryRecordCount >= ZipUtils.UINT16_MAX_VALUE)
                        || (centralDirectorySizeBytes >= ZipUtils.UINT32_MAX_VALUE)
                        || (centralDirectoryOffset >= ZipUtils.UINT32_MAX_VALUE);

        // If the end of central directory record does not need to be zip64, then just create /
        // modify the existing record from the original.
        if (!needsZip64) {
            return createZip32EocdRecordWithModifiedCentralDirectoryInfo(
                    original,
                    centralDirectoryRecordCount,
                    centralDirectorySizeBytes,
                    centralDirectoryOffset);
        }
        // For the zip64 case, recreate the full zip64 EoCD structure including the locator and
        // standard zip EoCD.
        return createZip64EocdRecordWithModifiedCentralDirectoryInfo(
                original,
                centralDirectoryRecordCount,
                centralDirectorySizeBytes,
                centralDirectoryOffset);
    }

    private static ByteBuffer createZip32EocdRecordWithModifiedCentralDirectoryInfo(
            ByteBuffer original,
            int centralDirectoryRecordCount,
            long centralDirectorySizeBytes,
            long centralDirectoryOffset) {
        // This is an edge case, but just in case the provided EoCD is zip64 but isn't required,
        // attempt to obtain just the standard zip EoCD from the buffer.
        if (ZipUtils.isEocdZip64(original)) {
            long zip64EocdLength =
                    original.getLong(ZIP64_EOCD_SIZE_OFFSET) + ZIP64_EOCD_REC_HEADER_SIZE;
            int zipEocdOffset = (int) zip64EocdLength + ZIP64_EOCD_LOCATOR_LENGTH;
            // Ensure the original buffer contains enough space for the zip EoCD record and has
            // the EoCD signature so that it can be used as is.
            original.position(0);
            if (original.remaining() < zipEocdOffset + ZIP_EOCD_REC_MIN_SIZE
                    || original.getInt(zipEocdOffset) != ZIP_EOCD_REC_SIG) {
                // A valid EoCD record does not exist, rebuild it to return to the caller.
                return createEocdRecord(
                        centralDirectoryRecordCount,
                        centralDirectorySizeBytes,
                        centralDirectoryOffset,
                        0);
            }
            // The original buffer has a valid EoCD record, update the position to point to
            // this and use it below to minimize changes to the existing record / file.
            original.position(zipEocdOffset);
        }
        ByteBuffer result = ByteBuffer.allocate(original.remaining());
        result.order(ByteOrder.LITTLE_ENDIAN);
        result.put(original.slice());
        result.flip();
        // centralDirectoryRecordCount is written twice, the first time for the number of records
        // on this disk, and the second for the total number of records on all disks. Since the
        // APK is contained in a single disk, the same value is written twice.
        ZipUtils.setUnsignedInt16(
                result, CD_RECORD_COUNT_ON_DISK_OFFSET, centralDirectoryRecordCount);
        ZipUtils.setUnsignedInt16(
                result, CD_RECORD_COUNT_TOTAL_OFFSET, centralDirectoryRecordCount);
        ZipUtils.setUnsignedInt32(result, CD_SIZE_OFFSET, centralDirectorySizeBytes);
        ZipUtils.setUnsignedInt32(result, CD_OFFSET_OFFSET, centralDirectoryOffset);
        return result;
    }

    private static ByteBuffer createZip64EocdRecordWithModifiedCentralDirectoryInfo(
            ByteBuffer original,
            int centralDirectoryRecordCount,
            long centralDirectorySizeBytes,
            long centralDirectoryOffset) {
        // For zip64, build the full buffer including the zip64 EoCD record, zip64 EoCD
        // locator, and the standard zip EoCD while maintaining any existing comments.
        ByteBuffer zip64Eocd = ByteBuffer.allocate(ZIP64_EOCD_REC_MIN_SIZE);
        zip64Eocd.order(ByteOrder.LITTLE_ENDIAN);
        zip64Eocd.putInt(ZIP64_EOCD_REC_SIG);
        zip64Eocd.putLong(ZIP64_EOCD_REC_MIN_SIZE - ZIP64_EOCD_REC_HEADER_SIZE);
        zip64Eocd.putShort((short) MIN_VERSION_SUPPORT_ZIP64); // version made by
        zip64Eocd.putShort((short) MIN_VERSION_SUPPORT_ZIP64); // version needed to extract
        zip64Eocd.putInt(0); // number of this disk
        zip64Eocd.putInt(0); // number of the disk with the start of the central directory
        zip64Eocd.putLong(centralDirectoryRecordCount); // total number of entries in the central
        // directory on this disk
        zip64Eocd.putLong(centralDirectoryRecordCount); // total number of entries in the central
        // directory on all disks
        zip64Eocd.putLong(centralDirectorySizeBytes);
        zip64Eocd.putLong(centralDirectoryOffset);
        zip64Eocd.flip();

        ByteBuffer zip64Locator = ByteBuffer.allocate(ZIP64_EOCD_LOCATOR_LENGTH);
        zip64Locator.order(ByteOrder.LITTLE_ENDIAN);
        zip64Locator.putInt(ZIP64_EOCD_LOCATOR_SIG);
        zip64Locator.putInt(0); // number of the disk with the start of the zip64 EoCD
        // Offset of the zip64 end of central directory record
        zip64Locator.putLong(centralDirectoryOffset + centralDirectorySizeBytes);
        zip64Locator.putInt(1); // total number of disks
        zip64Locator.flip();

        int commentLength;
        int commentLengthOffset;
        if (ZipUtils.isEocdZip64(original)) {
            long zip64EocdLength =
                    original.getLong(ZIP64_EOCD_SIZE_OFFSET) + ZIP64_EOCD_REC_HEADER_SIZE;
            commentLengthOffset =
                    (int) zip64EocdLength
                            + ZIP64_EOCD_LOCATOR_LENGTH
                            + ZIP_EOCD_COMMENT_LENGTH_FIELD_OFFSET;
            commentLength = ZipUtils.getUnsignedInt16(original, commentLengthOffset);
        } else {
            commentLengthOffset = ZIP_EOCD_COMMENT_LENGTH_FIELD_OFFSET;
            commentLength = ZipUtils.getUnsignedInt16(original, commentLengthOffset);
        }
        ByteBuffer eocd =
                createEocdRecord(
                        UINT16_MAX_VALUE, UINT32_MAX_VALUE, UINT32_MAX_VALUE, commentLength);
        // Copy the comment from the original EOCD buffer.
        if (commentLength > 0) {
            original.position(commentLengthOffset + 2);
            eocd.position(ZIP_EOCD_COMMENT_FIELD_OFFSET);
            // Create a temporary slice to avoid affecting the original buffer's limit.
            ByteBuffer comment = original.slice();
            comment.limit(commentLength);
            eocd.put(comment);
            eocd.flip();
        }

        ByteBuffer result =
                ByteBuffer.allocate(
                        zip64Eocd.remaining() + zip64Locator.remaining() + eocd.remaining());
        result.order(ByteOrder.LITTLE_ENDIAN);
        result.put(zip64Eocd);
        result.put(zip64Locator);
        result.put(eocd);
        result.flip();
        return result;
    }

    private static ByteBuffer createEocdRecord(
            int centralDirectoryRecordCount,
            long centralDirectorySizeBytes,
            long centralDirectoryOffset,
            int commentLength) {
        ByteBuffer eocd = ByteBuffer.allocate(ZIP_EOCD_REC_MIN_SIZE + commentLength);
        eocd.order(ByteOrder.LITTLE_ENDIAN);
        eocd.putInt(ZIP_EOCD_REC_SIG);
        ZipUtils.putUnsignedInt16(eocd, 0); // number of this disk
        ZipUtils.putUnsignedInt16(eocd, 0); // disk where central directory starts
        // The central directory record count is written twice intentionally; the first for the
        // number of records on this disk, and the second for the number of records on all disks.
        ZipUtils.putUnsignedInt16(eocd, centralDirectoryRecordCount);
        ZipUtils.putUnsignedInt16(eocd, centralDirectoryRecordCount);
        ZipUtils.putUnsignedInt32(eocd, centralDirectorySizeBytes);
        ZipUtils.putUnsignedInt32(eocd, centralDirectoryOffset);
        ZipUtils.putUnsignedInt16(eocd, commentLength);
        eocd.position(0);
        return eocd;
    }

    public static ByteBuffer createWithPaddedComment(ByteBuffer original, int padding) {
        ByteBuffer result = ByteBuffer.allocate((int) original.remaining() + padding);
        result.order(ByteOrder.LITTLE_ENDIAN);
        result.put(original.slice());
        result.rewind();
        ZipUtils.updateZipEocdCommentLen(result);
        return result;
    }
}
