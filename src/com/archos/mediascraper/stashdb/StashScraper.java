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

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;

import com.archos.mediascraper.MovieTags;
import com.archos.mediascraper.ScrapeDetailResult;
import com.archos.mediascraper.ScrapeSearchResult;
import com.archos.mediascraper.ScrapeStatus;
import com.archos.mediascraper.ScraperImage;
import com.archos.mediascraper.SearchResult;
import com.archos.mediascraper.preprocess.SearchInfo;
import com.archos.mediascraper.xml.BaseScraper2;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Native StashDB Scraper replacing legacy TMDb / TVDB scrapers.
 * Supports sidecar UUID detection, instant OSHash matching, and regex studio/performer text search.
 */
public class StashScraper extends BaseScraper2 {
    private static final Logger log = LoggerFactory.getLogger(StashScraper.class);

    private final StashDbClient mClient;

    public StashScraper(Context context) {
        super(context);
        this.mClient = new StashDbClient(context);
    }

    public StashScraper(Context context, StashDbClient client) {
        super(context);
        this.mClient = client;
    }

    @Override
    public ScrapeSearchResult getMatches2(SearchInfo info, int maxItems) {
        if (info == null) {
            return new ScrapeSearchResult(null, true, ScrapeStatus.ERROR, null);
        }

        Uri fileUri = info.getFile();
        File localFile = (fileUri != null && "file".equalsIgnoreCase(fileUri.getScheme()))
                ? new File(fileUri.getPath())
                : null;

        List<StashDbClient.StashScene> scenes = new ArrayList<>();
        ScrapeStatus status = ScrapeStatus.NOT_FOUND;
        Throwable errorReason = null;

        try {
            // Step 1: Check for Sidecar UUID (.nfo, .id, .json, .txt)
            if (localFile != null) {
                String sidecarUuid = StashPreprocessor.detectSidecarStashId(localFile);
                if (sidecarUuid != null) {
                    log.info("getMatches2: querying StashDB by sidecar UUID: {}", sidecarUuid);
                    StashDbClient.StashScene scene = mClient.findSceneById(sidecarUuid);
                    if (scene != null) {
                        scenes.add(scene);
                        status = ScrapeStatus.OKAY;
                    }
                }
            }

            // Step 2: Instant OSHash fingerprint match
            if (scenes.isEmpty() && localFile != null) {
                String oshash = StashDbHasher.computeOsHash(localFile);
                if (oshash != null) {
                    log.info("getMatches2: querying StashDB by OSHASH: {}", oshash);
                    List<StashDbClient.StashScene> hashMatches = mClient.findSceneByHash(oshash);
                    if (hashMatches != null && !hashMatches.isEmpty()) {
                        scenes.addAll(hashMatches);
                        status = ScrapeStatus.OKAY;
                    }
                }
            }

            // Step 3: Text Search via Scene Preprocessor
            if (scenes.isEmpty()) {
                String filename = (localFile != null) ? localFile.getName() : info.getName();
                StashPreprocessor.ParsedSceneInfo parsed = StashPreprocessor.parseFilename(filename);

                String searchTerm = buildSearchTerm(parsed, info.getName());
                log.info("getMatches2: querying StashDB text search for: '{}'", searchTerm);

                List<StashDbClient.StashScene> textMatches = mClient.searchScenes(searchTerm);
                if (textMatches != null && !textMatches.isEmpty()) {
                    scenes.addAll(textMatches);
                    status = ScrapeStatus.OKAY;
                }
            }
        } catch (IOException e) {
            log.error("getMatches2: network/API error: {}", e.getMessage());
            status = ScrapeStatus.ERROR;
            errorReason = e;
        }

        if (scenes.isEmpty()) {
            return new ScrapeSearchResult(Collections.emptyList(), true, status, errorReason);
        }

        List<SearchResult> results = new ArrayList<>();
        for (StashDbClient.StashScene sc : scenes) {
            SearchResult sr = new SearchResult();
            sr.setMovie();
            sr.setFile(info.getFile());
            sr.setTitle(sc.title);
            sr.setOriginalTitle(sc.title);
            sr.setYear(extractYear(sc.releaseDate));
            sr.setScraper(this);
            sr.setExtra(sc.id); // Stash UUID
            results.add(sr);
        }

        return new ScrapeSearchResult(results, true, ScrapeStatus.OKAY, null);
    }

    public ScrapeDetailResult search(SearchInfo info) {
        ScrapeSearchResult searchResult = getMatches2(info, 1);
        if (searchResult.status != ScrapeStatus.OKAY || searchResult.results == null || searchResult.results.isEmpty()) {
            return new ScrapeDetailResult(null, true, null, searchResult.status, searchResult.reason);
        }

        SearchResult bestMatch = searchResult.results.get(0);
        return getDetails(bestMatch, null);
    }

    public ScrapeDetailResult getDetails(SearchResult result, Bundle options) {
        if (result == null) {
            return new ScrapeDetailResult(null, true, null, ScrapeStatus.ERROR, null);
        }

        String sceneId = result.getExtra();
        if (sceneId == null || sceneId.trim().isEmpty()) {
            return new ScrapeDetailResult(null, true, null, ScrapeStatus.ERROR, null);
        }

        try {
            StashDbClient.StashScene scene = mClient.findSceneById(sceneId);
            if (scene == null) {
                return new ScrapeDetailResult(null, true, null, ScrapeStatus.NOT_FOUND, null);
            }

            MovieTags tags = new MovieTags();
            tags.setTitle(scene.title != null && !scene.title.isEmpty() ? scene.title : result.getTitle());
            tags.setPlot(scene.details);
            tags.setOnlineId(Long.parseLong(scene.id.replaceAll("[^0-9]", "").substring(0, Math.min(8, scene.id.replaceAll("[^0-9]", "").length()))));
            tags.setFile(result.getFile());

            if (scene.releaseDate != null && scene.releaseDate.length() >= 4) {
                try {
                    tags.setYear(Integer.parseInt(scene.releaseDate.substring(0, 4)));
                } catch (NumberFormatException ignored) {}
            }

            if (scene.studio != null && scene.studio.name != null) {
                tags.addStudio(scene.studio.name);
                tags.addDirector(scene.studio.name);
            }

            // Cis-female performers prioritized
            List<String> cisFemales = scene.getCisFemalePerformerNames();
            for (String femaleName : cisFemales) {
                tags.addActor(femaleName, "Performer");
            }

            // Also add all performers
            for (StashDbClient.StashPerformer perf : scene.performers) {
                if (perf.name != null && !cisFemales.contains(perf.name.trim())) {
                    tags.addActor(perf.name.trim(), "Actor");
                }
            }

            // Tags / Genres
            for (String tag : scene.tags) {
                tags.addGenre(tag);
            }

            // Posters & Fanart images
            if (mContext != null && scene.images != null) {
                for (StashDbClient.StashImage img : scene.images) {
                    if (img.url != null && !img.url.isEmpty()) {
                        ScraperImage poster = new ScraperImage(ScraperImage.Type.MOVIE_POSTER, tags.getTitle());
                        poster.setLargeUrl(img.url);
                        poster.setThumbUrl(img.url);
                        poster.generateFileNames(mContext);
                        tags.addPoster(poster);

                        ScraperImage backdrop = new ScraperImage(ScraperImage.Type.MOVIE_BACKDROP, tags.getTitle());
                        backdrop.setLargeUrl(img.url);
                        backdrop.setThumbUrl(img.url);
                        backdrop.generateFileNames(mContext);
                        tags.addBackdrop(backdrop);
                    }
                }
            }

            return new ScrapeDetailResult(tags, true, null, ScrapeStatus.OKAY, null);
        } catch (IOException e) {
            log.error("getDetails: error fetching scene {}: {}", sceneId, e.getMessage());
            return new ScrapeDetailResult(null, true, null, ScrapeStatus.ERROR, e);
        }
    }

    private static String buildSearchTerm(StashPreprocessor.ParsedSceneInfo parsed, String fallbackName) {
        StringBuilder sb = new StringBuilder();
        if (parsed.performers != null && !parsed.performers.isEmpty()) {
            sb.append(parsed.performers.get(0));
        }
        if (parsed.studio != null && !"Unknown_Studio".equalsIgnoreCase(parsed.studio)) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(parsed.studio);
        }
        if (parsed.title != null && !parsed.title.isEmpty() && (parsed.performers == null || parsed.performers.isEmpty())) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(parsed.title);
        }
        return sb.length() > 0 ? sb.toString() : fallbackName;
    }

    private static String extractYear(String dateStr) {
        if (dateStr != null && dateStr.length() >= 4) {
            return dateStr.substring(0, 4);
        }
        return "";
    }
}
