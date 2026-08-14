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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StashIntegrationTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void testOsHashComputation() throws IOException {
        File dummyFile = tempFolder.newFile("test_scene.mp4");
        int fileSize = 65536 * 3; // 192 KB
        byte[] dummyData = new byte[fileSize];
        for (int i = 0; i < dummyData.length; i++) {
            dummyData[i] = (byte) (i % 256);
        }

        try (FileOutputStream fos = new FileOutputStream(dummyFile)) {
            fos.write(dummyData);
        }

        String hash = StashDbHasher.computeOsHash(dummyFile);
        assertNotNull("OSHash should not be null for files >= 128KB", hash);
        assertEquals("OSHash must be 16 hex chars", 16, hash.length());

        // Verify mathematically against raw bytes
        long expectedHash = fileSize;
        ByteBuffer headBuf = ByteBuffer.wrap(dummyData, 0, 65536).order(ByteOrder.LITTLE_ENDIAN);
        while (headBuf.hasRemaining()) {
            expectedHash += headBuf.getLong();
        }

        ByteBuffer tailBuf = ByteBuffer.wrap(dummyData, fileSize - 65536, 65536).order(ByteOrder.LITTLE_ENDIAN);
        while (tailBuf.hasRemaining()) {
            expectedHash += tailBuf.getLong();
        }

        String expectedHex = String.format("%016x", expectedHash);
        assertEquals("Calculated OSHash must match expected uint64 sum", expectedHex, hash);
    }

    @Test
    public void testSceneDotFilenamePreprocessing() {
        String filename1 = "TonightsGirlfriend.25.10.17.Maisey.Monroe.XXX.1080p.HEVC.x265.PRT.mp4";
        StashPreprocessor.ParsedSceneInfo info1 = StashPreprocessor.parseFilename(filename1);
        assertTrue(info1.hasClues);
        assertEquals("2025-10-17", info1.date);
        assertEquals("2025", info1.year);
        assertEquals(1, info1.performers.size());
        assertEquals("Maisey Monroe", info1.performers.get(0));

        String filename2 = "POVMasters.26.05.19.Mia.River.XXX.1080p.HEVC.x265.PRT.mp4";
        StashPreprocessor.ParsedSceneInfo info2 = StashPreprocessor.parseFilename(filename2);
        assertTrue(info2.hasClues);
        assertEquals("POV Masters", info2.studio);
        assertEquals("2026-05-19", info2.date);
        assertEquals("Mia River", info2.performers.get(0));
    }

    @Test
    public void testUnderscoreFilenamePreprocessing() {
        String filename = "2025-09-27_HotGirlsRaw_Mia_River_Naughty_And_Playful.mp4";
        StashPreprocessor.ParsedSceneInfo info = StashPreprocessor.parseFilename(filename);
        assertTrue(info.hasClues);
        assertEquals("2025-09-27", info.date);
        assertEquals("2025", info.year);
        assertEquals(1, info.performers.size());
        assertEquals("Mia River", info.performers.get(0));
        assertEquals("Naughty And Playful", info.title);
    }

    @Test
    public void testStudioNormalization() {
        assertEquals("L.A. New Girl", StashPreprocessor.normalizeStudio("lanewgirl"));
        assertEquals("Net Video Girls", StashPreprocessor.normalizeStudio("netvideogirls"));
        assertEquals("18_Lust", StashPreprocessor.normalizeStudio("18lust"));
        assertEquals("VIP4K", StashPreprocessor.normalizeStudio("vip4k"));
        assertEquals("Czech Casting", StashPreprocessor.normalizeStudio("czechcasting"));
        assertEquals("Argentina Casting", StashPreprocessor.normalizeStudio("argentinacasting"));
    }

    @Test
    public void testCisFemalePerformerFiltering() {
        List<Map<String, Object>> performers = new ArrayList<>();

        Map<String, Object> male = new HashMap<>();
        Map<String, Object> maleDetails = new HashMap<>();
        maleDetails.put("name", "John Strong");
        maleDetails.put("gender", "MALE");
        male.put("performer", maleDetails);
        performers.add(male);

        Map<String, Object> female = new HashMap<>();
        Map<String, Object> femaleDetails = new HashMap<>();
        femaleDetails.put("name", "Maisey Monroe");
        femaleDetails.put("gender", "FEMALE");
        female.put("performer", femaleDetails);
        performers.add(female);

        List<String> cisFemales = StashPreprocessor.filterCisFemalePerformers(performers);
        assertEquals(1, cisFemales.size());
        assertEquals("Maisey Monroe", cisFemales.get(0));
    }
}
