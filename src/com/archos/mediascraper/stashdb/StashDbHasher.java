// Copyright 2026 Nova Video Player StashDB Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.archos.mediascraper.stashdb;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;

/**
 * Fast 64-bit OpenSubtitles Hash (OSHash) calculator for local video files.
 * Uses 64KB head + 64KB tail uint64 addition, taking <5ms on local storage.
 */
public final class StashDbHasher {
    private static final Logger log = LoggerFactory.getLogger(StashDbHasher.class);
    private static final int CHUNK_SIZE = 65536; // 64 KB

    private StashDbHasher() {}

    /**
     * Computes the 16-character hexadecimal OSHash for the given file.
     * Returns null if the file does not exist, cannot be read, or is smaller than 128KB.
     */
    public static String computeOsHash(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            return null;
        }

        long size = file.length();
        if (size < CHUNK_SIZE * 2) {
            log.warn("computeOsHash: file too small ({} bytes) for {}", size, file.getName());
            return null;
        }

        long hash = size;
        try (FileInputStream fis = new FileInputStream(file);
             FileChannel channel = fis.getChannel()) {

            ByteBuffer buffer = ByteBuffer.allocateDirect(CHUNK_SIZE);
            buffer.order(ByteOrder.LITTLE_ENDIAN);

            // 1. Read first 64KB
            channel.read(buffer);
            buffer.flip();
            while (buffer.hasRemaining()) {
                hash += buffer.getLong();
            }

            // 2. Read last 64KB
            buffer.clear();
            channel.position(Math.max(0, size - CHUNK_SIZE));
            channel.read(buffer);
            buffer.flip();
            while (buffer.hasRemaining()) {
                hash += buffer.getLong();
            }

            return String.format("%016x", hash);
        } catch (IOException e) {
            log.error("computeOsHash: error reading {}: {}", file.getAbsolutePath(), e.getMessage());
            return null;
        }
    }

    public static String computeOsHash(String filepath) {
        if (filepath == null || filepath.isEmpty()) {
            return null;
        }
        return computeOsHash(new File(filepath));
    }
}
