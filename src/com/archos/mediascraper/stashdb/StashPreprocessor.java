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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Intelligent adult scene filename preprocessor, noise filter, studio normalizer,
 * and sidecar UUID extractor ported directly from the proven Python suite.
 */
public final class StashPreprocessor {
    private static final Logger log = LoggerFactory.getLogger(StashPreprocessor.class);

    private static final Map<String, String> KNOWN_STUDIOS = new HashMap<>();
    static {
        KNOWN_STUDIOS.put("xxxjobinterviews", "XXXJobInterviews");
        KNOWN_STUDIOS.put("vip4k", "VIP4K");
        KNOWN_STUDIOS.put("bananafever", "Banana Fever");
        KNOWN_STUDIOS.put("jaxslayher", "Jax Slayher");
        KNOWN_STUDIOS.put("jaxslayhertv", "Jax Slayher");
        KNOWN_STUDIOS.put("cuckhunter", "Cuckhunter");
        KNOWN_STUDIOS.put("exploitedteens", "Exploited Teens");
        KNOWN_STUDIOS.put("czechcasting", "Czech Casting");
        KNOWN_STUDIOS.put("argentinacasting", "Argentina Casting");
        KNOWN_STUDIOS.put("diselvids", "Disel Vids");
        KNOWN_STUDIOS.put("lanewgirl", "L.A. New Girl");
        KNOWN_STUDIOS.put("la_new_girl", "L.A. New Girl");
        KNOWN_STUDIOS.put("netvideogirls", "Net Video Girls");
        KNOWN_STUDIOS.put("netgirl", "Net Girl");
        KNOWN_STUDIOS.put("puretaboo", "Pure Taboo");
        KNOWN_STUDIOS.put("mydirtyuncle", "My Dirty Uncle");
        KNOWN_STUDIOS.put("povmasters", "POV Masters");
        KNOWN_STUDIOS.put("18lust", "18_Lust");
        KNOWN_STUDIOS.put("18_lust", "18_Lust");
        KNOWN_STUDIOS.put("blacked", "Blacked");
        KNOWN_STUDIOS.put("tushy", "Tushy");
        KNOWN_STUDIOS.put("tushyraw", "Tushy Raw");
        KNOWN_STUDIOS.put("vixen", "Vixen");
        KNOWN_STUDIOS.put("deeper", "Deeper");
    }

    private static final Set<String> NOISE_TOKENS = new HashSet<>(Arrays.asList(
            "xxx", "1080p", "720p", "2160p", "4k", "2k", "hd", "sd",
            "hevc", "x265", "x264", "h.264", "h.265", "prt", "swe6rus",
            "bushproductions", "slr", "vr", "mp4", "mkv", "avi", "mov",
            "webdl", "lq", "aac", "dts"
    ));

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern YYYY_MM_DD = Pattern.compile(
            "(?:^|[\\s_.-])(19\\d\\d|20\\d\\d)[-_.](\\d{2})[-_.](\\d{2})(?:[\\s_.-]|$)"
    );

    private static final Pattern YY_MM_DD = Pattern.compile(
            "(?:^|[\\s_.-])(2[0-9])\\.(\\d{2})\\.(\\d{2})(?:[\\s_.-]|$)"
    );

    public static class ParsedSceneInfo {
        public boolean hasClues = false;
        public String studio = "Unknown_Studio";
        public String date = null;
        public String year = null;
        public List<String> performers = new ArrayList<>();
        public String title = "";
        public String directStashId = null;

        @Override
        public String toString() {
            return "ParsedSceneInfo{" +
                    "hasClues=" + hasClues +
                    ", studio='" + studio + '\'' +
                    ", date='" + date + '\'' +
                    ", performers=" + performers +
                    ", title='" + title + '\'' +
                    ", directStashId='" + directStashId + '\'' +
                    '}';
        }
    }

    private StashPreprocessor() {}

    /**
     * Checks if a sidecar file (.nfo, .id, .json, .txt) exists next to the video and contains a direct StashDB UUID.
     */
    public static String detectSidecarStashId(File videoFile) {
        if (videoFile == null || !videoFile.exists()) return null;

        String parent = videoFile.getParent();
        String nameWithoutExt = removeExtension(videoFile.getName());
        String[] sidecarExts = {".nfo", ".id", ".json", ".txt"};

        for (String ext : sidecarExts) {
            File sidecar = new File(parent, nameWithoutExt + ext);
            if (sidecar.exists() && sidecar.isFile()) {
                try (BufferedReader reader = new BufferedReader(new FileReader(sidecar))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        Matcher m = UUID_PATTERN.matcher(line);
                        if (m.find()) {
                            log.info("detectSidecarStashId: found Stash UUID {} in {}", m.group(1), sidecar.getName());
                            return m.group(1).toLowerCase(Locale.ROOT);
                        }
                    }
                } catch (IOException e) {
                    log.warn("detectSidecarStashId: read error {}: {}", sidecar.getName(), e.getMessage());
                }
            }
        }
        return null;
    }

    /**
     * Parses release filenames in both Scene Dot format and Underscore format.
     */
    public static ParsedSceneInfo parseFilename(String filename) {
        ParsedSceneInfo info = new ParsedSceneInfo();
        if (filename == null || filename.trim().isEmpty()) {
            return info;
        }

        String name = removeExtension(new File(filename).getName());

        // 1. Date extraction
        Matcher mYyyy = YYYY_MM_DD.matcher(name);
        Matcher mYy = YY_MM_DD.matcher(name);
        if (mYyyy.find()) {
            info.date = mYyyy.group(1) + "-" + mYyyy.group(2) + "-" + mYyyy.group(3);
            info.year = mYyyy.group(1);
        } else if (mYy.find()) {
            info.date = "20" + mYy.group(1) + "-" + mYy.group(2) + "-" + mYy.group(3);
            info.year = "20" + mYy.group(1);
        }

        // 2. Scene Dot Format: Studio.YY.MM.DD.Performer.Title.XXX...
        if (name.contains(".") && (mYy.reset().find() || mYyyy.reset().find())) {
            String[] parts = name.split("\\.");
            String studioRaw = parts[0];
            info.studio = normalizeStudio(studioRaw);

            int dateIdx = -1;
            for (int i = 0; i < parts.length - 2; i++) {
                if ((parts[i].matches("^\\d{2}$") || parts[i].matches("^\\d{4}$")) &&
                        parts[i + 1].matches("^\\d{2}$") &&
                        parts[i + 2].matches("^\\d{2}$")) {
                    dateIdx = i + 3;
                    break;
                }
            }

            if (dateIdx > 0 && dateIdx < parts.length) {
                List<String> cleanRem = new ArrayList<>();
                for (int i = dateIdx; i < parts.length; i++) {
                    String token = parts[i];
                    if (NOISE_TOKENS.contains(token.toLowerCase(Locale.ROOT))) {
                        break;
                    }
                    cleanRem.add(token);
                }

                if (cleanRem.size() >= 2) {
                    info.hasClues = true;
                    info.performers.add(cleanRem.get(0) + " " + cleanRem.get(1));
                    if (cleanRem.size() == 2) {
                        info.title = cleanRem.get(0) + " " + cleanRem.get(1);
                    } else {
                        StringBuilder sb = new StringBuilder();
                        for (int k = 2; k < cleanRem.size(); k++) {
                            if (sb.length() > 0) sb.append(" ");
                            sb.append(cleanRem.get(k));
                        }
                        info.title = sb.toString();
                    }
                    return info;
                } else if (cleanRem.size() == 1) {
                    info.hasClues = true;
                    info.performers.add(cleanRem.get(0));
                    info.title = cleanRem.get(0);
                    return info;
                }
            }
        }

        // 3. Underscore Format: YYYY-MM-DD_Studio_Performer_Title or Studio_Performer_Title
        String cleanName = name;
        if (info.date != null) {
            cleanName = cleanName.replaceFirst("^(?:19\\d\\d|20\\d\\d)[-_.]\\d{2}[-_.]\\d{2}[-_.]?", "");
        }

        String[] partsU = cleanName.split("_");
        info.title = cleanName.replace('_', ' ').trim();
        info.hasClues = (info.date != null);

        if (partsU.length > 0) {
            String lower0 = partsU[0].toLowerCase(Locale.ROOT);
            if (KNOWN_STUDIOS.containsKey(lower0)) {
                info.studio = KNOWN_STUDIOS.get(lower0);
                info.hasClues = true;
                if (partsU.length >= 3) {
                    info.performers.add(partsU[1] + " " + partsU[2]);
                    StringBuilder sb = new StringBuilder();
                    for (int k = 3; k < partsU.length; k++) {
                        if (sb.length() > 0) sb.append(" ");
                        sb.append(partsU[k]);
                    }
                    info.title = sb.length() > 0 ? sb.toString() : (partsU[1] + " " + partsU[2]);
                } else if (partsU.length == 2) {
                    info.performers.add(partsU[1]);
                    info.title = partsU[1];
                }
            } else {
                for (Map.Entry<String, String> entry : KNOWN_STUDIOS.entrySet()) {
                    if (lower0.contains(entry.getKey())) {
                        info.studio = entry.getValue();
                        info.hasClues = true;
                        if (partsU.length >= 3) {
                            info.performers.add(partsU[1] + " " + partsU[2]);
                        }
                        break;
                    }
                }
            }
        }

        if (name.length() <= 3 || name.equalsIgnoreCase("video") || name.equalsIgnoreCase("clip")) {
            info.hasClues = false;
        }

        return info;
    }

    public static String normalizeStudio(String studioRaw) {
        if (studioRaw == null) return "Unknown_Studio";
        String lower = studioRaw.toLowerCase(Locale.ROOT).replaceAll("[\\s_-]+", "");
        return KNOWN_STUDIOS.getOrDefault(lower, studioRaw);
    }

    /**
     * Filters performers to strictly return cis-female performers (gender == "FEMALE").
     */
    public static List<String> filterCisFemalePerformers(List<Map<String, Object>> performersList) {
        if (performersList == null || performersList.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> females = new ArrayList<>();
        List<String> fallbacks = new ArrayList<>();

        for (Map<String, Object> pObj : performersList) {
            @SuppressWarnings("unchecked")
            Map<String, Object> perf = (Map<String, Object>) pObj.get("performer");
            if (perf == null) continue;

            String name = (String) perf.get("name");
            if (name == null || name.trim().isEmpty()) continue;

            String gender = (String) perf.get("gender");
            if ("FEMALE".equalsIgnoreCase(gender)) {
                females.add(name.trim());
            } else if (!"MALE".equalsIgnoreCase(gender)) {
                fallbacks.add(name.trim());
            }
        }

        return !females.isEmpty() ? females : fallbacks;
    }

    private static String removeExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
