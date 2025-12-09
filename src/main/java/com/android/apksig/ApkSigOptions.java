/*
 * Copyright (C) 2025 The Android Open Source Project
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

/**
 * This class allows global options to be set to modify the behavior of the library when signing and
 * verifying APKs.
 */
public final class ApkSigOptions {
    /**
     * Enum containing the allocation modes that are available when allocating buffers for files.
     */
    public enum ByteBufferAllocationMode {
        /**
         * Use heap based ByteBuffer instances for file allocation (ie ByteBuffer#allocate).
         */
        HEAP,
        /**
         * Use off-heap, native ByteBuffer instances for file allocation (ie
         * ByteBuffer#allocateDirect).
         */
        DIRECT,
    }

    private ByteBufferAllocationMode mByteBufferAllocationMode = ByteBufferAllocationMode.HEAP;

    private static final ApkSigOptions sInstance = new ApkSigOptions();

    private ApkSigOptions() {
    }

    /**
     * Returns the single instance of this class.
     */
    public static ApkSigOptions getInstance() {
        return sInstance;
    }

    /**
     * Sets the {@link java.nio.ByteBuffer} allocation mode when allocating buffers for files.
     *
     * <p>Note, this does not change all {@code ByteBuffer} allocation calls; those not directly
     * used for reading files will still default to heap based allocation.
     *
     * @param mode {@code ByteBufferAllocationMode} to be used for allocation
     */
    public void setByteBufferAllocationMode(ByteBufferAllocationMode mode) {
        if (mode == null) {
            throw new NullPointerException("ByteBufferAllocationMode cannot be null");
        }
        mByteBufferAllocationMode = mode;
    }

    /**
     * Returns the {@code ByteBufferAllocationMode} to be used when allocation buffers for files.
     */
    public ByteBufferAllocationMode getByteBufferAllocationMode() {
        return mByteBufferAllocationMode;
    }
}
