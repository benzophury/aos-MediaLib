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
import androidx.preference.PreferenceManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * High-performance GraphQL Client for StashDB (https://stashdb.org/graphql).
 */
public class StashDbClient {
    private static final Logger log = LoggerFactory.getLogger(StashDbClient.class);

    public static final String DEFAULT_STASHDB_URL = "https://stashdb.org/graphql";
    public static final String PREF_STASHDB_URL = "stashdb_graphql_url";
    public static final String PREF_STASHDB_API_KEY = "stashdb_api_key";

    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");

    private final Context mContext;
    private final String mExplicitEndpointUrl;
    private final String mExplicitApiKey;
    private final OkHttpClient mHttpClient;

    public static class StashPerformer {
        public String id;
        public String name;
        public String gender;
        public String disambiguation;
        public String imagePath;
    }

    public static class StashStudio {
        public String id;
        public String name;
        public String imagePath;
    }

    public static class StashImage {
        public String id;
        public String url;
        public int width;
        public int height;
    }

    public static class StashScene {
        public String id;
        public String title;
        public String releaseDate;
        public String details;
        public int durationSec;
        public StashStudio studio;
        public List<StashPerformer> performers = new ArrayList<>();
        public List<String> tags = new ArrayList<>();
        public List<StashImage> images = new ArrayList<>();

        public List<String> getCisFemalePerformerNames() {
            List<String> females = new ArrayList<>();
            List<String> fallbacks = new ArrayList<>();
            for (StashPerformer p : performers) {
                if (p.name == null || p.name.trim().isEmpty()) continue;
                if ("FEMALE".equalsIgnoreCase(p.gender)) {
                    females.add(p.name.trim());
                } else if (!"MALE".equalsIgnoreCase(p.gender)) {
                    fallbacks.add(p.name.trim());
                }
            }
            return !females.isEmpty() ? females : fallbacks;
        }

        public List<String> getAllPerformerNames() {
            List<String> names = new ArrayList<>();
            for (StashPerformer p : performers) {
                if (p.name != null && !p.name.trim().isEmpty()) {
                    names.add(p.name.trim());
                }
            }
            return names;
        }
    }

    public StashDbClient(Context context) {
        this.mContext = context != null ? context.getApplicationContext() : null;
        this.mExplicitEndpointUrl = null;
        this.mExplicitApiKey = null;
        this.mHttpClient = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build();
    }

    public StashDbClient(String endpointUrl, String apiKey) {
        this.mContext = null;
        this.mExplicitEndpointUrl = (endpointUrl != null && !endpointUrl.trim().isEmpty()) ? endpointUrl.trim() : DEFAULT_STASHDB_URL;
        this.mExplicitApiKey = apiKey != null ? apiKey.trim() : "";
        this.mHttpClient = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build();
    }

    public String getEndpointUrl() {
        if (mExplicitEndpointUrl != null) return mExplicitEndpointUrl;
        if (mContext != null) {
            String url = PreferenceManager.getDefaultSharedPreferences(mContext).getString(PREF_STASHDB_URL, DEFAULT_STASHDB_URL);
            if (url != null && !url.trim().isEmpty()) return url.trim();
        }
        return DEFAULT_STASHDB_URL;
    }

    public String getApiKey() {
        if (mExplicitApiKey != null) return mExplicitApiKey;
        if (mContext != null) {
            return PreferenceManager.getDefaultSharedPreferences(mContext).getString(PREF_STASHDB_API_KEY, "").trim();
        }
        return "";
    }

    private static final String QUERY_FIND_BY_HASH =
            "query FindSceneByHash($fingerprints: [[FingerprintQueryInput!]!]!) {\n" +
            "  findScenesBySceneFingerprints(fingerprints: $fingerprints) {\n" +
            "    id\n" +
            "    title\n" +
            "    release_date\n" +
            "    details\n" +
            "    duration\n" +
            "    studio { id name image_path }\n" +
            "    performers { performer { id name gender disambiguation image_path } }\n" +
            "    tags { id name }\n" +
            "    images { id url width height }\n" +
            "  }\n" +
            "}";

    private static final String QUERY_FIND_BY_ID =
            "query FindSceneById($id: ID!) {\n" +
            "  findScene(id: $id) {\n" +
            "    id\n" +
            "    title\n" +
            "    release_date\n" +
            "    details\n" +
            "    duration\n" +
            "    studio { id name image_path }\n" +
            "    performers { performer { id name gender disambiguation image_path } }\n" +
            "    tags { id name }\n" +
            "    images { id url width height }\n" +
            "  }\n" +
            "}";

    private static final String QUERY_SEARCH_SCENES =
            "query SearchScene($term: String!) {\n" +
            "  searchScenes(term: $term) {\n" +
            "    count\n" +
            "    scenes {\n" +
            "      id\n" +
            "      title\n" +
            "      release_date\n" +
            "      details\n" +
            "      duration\n" +
            "      studio { id name image_path }\n" +
            "      performers { performer { id name gender disambiguation image_path } }\n" +
            "      tags { id name }\n" +
            "      images { id url width height }\n" +
            "    }\n" +
            "  }\n" +
            "}";

    public List<StashScene> findSceneByHash(String oshash) throws IOException {
        if (oshash == null || oshash.trim().isEmpty()) {
            return Collections.emptyList();
        }

        try {
            JSONObject varInput = new JSONObject();
            varInput.put("hash", oshash.trim().toLowerCase());
            varInput.put("algorithm", "OSHASH");

            JSONArray innerArray = new JSONArray();
            innerArray.put(varInput);

            JSONArray outerArray = new JSONArray();
            outerArray.put(innerArray);

            JSONObject variables = new JSONObject();
            variables.put("fingerprints", outerArray);

            JSONObject response = executeQuery(QUERY_FIND_BY_HASH, variables);
            JSONObject data = response.optJSONObject("data");
            if (data == null) return Collections.emptyList();

            JSONArray scenesArray = data.optJSONArray("findScenesBySceneFingerprints");
            if (scenesArray == null || scenesArray.length() == 0) return Collections.emptyList();

            // First element is the array of scenes matching inner fingerprint array
            JSONArray matches = scenesArray.optJSONArray(0);
            if (matches == null) return Collections.emptyList();

            List<StashScene> result = new ArrayList<>();
            for (int i = 0; i < matches.length(); i++) {
                JSONObject sc = matches.optJSONObject(i);
                if (sc != null) {
                    result.add(parseScene(sc));
                }
            }
            return result;
        } catch (JSONException e) {
            log.error("findSceneByHash: JSON error: {}", e.getMessage());
            throw new IOException("JSON serialization error", e);
        }
    }

    public StashScene findSceneById(String sceneId) throws IOException {
        if (sceneId == null || sceneId.trim().isEmpty()) {
            return null;
        }

        try {
            JSONObject variables = new JSONObject();
            variables.put("id", sceneId.trim());

            JSONObject response = executeQuery(QUERY_FIND_BY_ID, variables);
            JSONObject data = response.optJSONObject("data");
            if (data == null) return null;

            JSONObject sceneObj = data.optJSONObject("findScene");
            return sceneObj != null ? parseScene(sceneObj) : null;
        } catch (JSONException e) {
            log.error("findSceneById: JSON error: {}", e.getMessage());
            throw new IOException("JSON serialization error", e);
        }
    }

    public List<StashScene> searchScenes(String term) throws IOException {
        if (term == null || term.trim().isEmpty()) {
            return Collections.emptyList();
        }

        String cleanTerm = term.trim();
        log.info("searchScenes: Querying StashDB for term='{}'", cleanTerm);

        try {
            JSONObject variables = new JSONObject();
            variables.put("term", cleanTerm);
            variables.put("limit", 25);

            JSONObject response = executeQuery(QUERY_SEARCH_SCENES, variables);
            JSONObject data = response.optJSONObject("data");
            if (data == null) {
                log.warn("searchScenes: null data in response for term='{}'", cleanTerm);
                return Collections.emptyList();
            }

            JSONObject searchScenes = data.optJSONObject("searchScenes");
            if (searchScenes == null) {
                log.warn("searchScenes: null searchScenes object for term='{}'", cleanTerm);
                return Collections.emptyList();
            }

            JSONArray scenes = searchScenes.optJSONArray("scenes");
            if (scenes == null) {
                log.info("searchScenes: 0 scenes found for term='{}'", cleanTerm);
                return Collections.emptyList();
            }

            List<StashScene> result = new ArrayList<>();
            for (int i = 0; i < scenes.length(); i++) {
                JSONObject sc = scenes.optJSONObject(i);
                if (sc != null) {
                    result.add(parseScene(sc));
                }
            }
            log.info("searchScenes: Found {} scenes for term='{}'", result.size(), cleanTerm);
            return result;
        } catch (JSONException e) {
            log.error("searchScenes: JSON error: {}", e.getMessage(), e);
            throw new IOException("JSON serialization error", e);
        }
    }

    private JSONObject executeQuery(String query, JSONObject variables) throws IOException {
        String endpointUrl = getEndpointUrl();
        String apiKey = getApiKey();

        if (apiKey.isEmpty() && endpointUrl.contains("stashdb.org")) {
            log.warn("executeQuery: StashDB API key is not configured! Please enter your API key in Settings -> Stash Preferences.");
        }

        try {
            JSONObject payload = new JSONObject();
            payload.put("query", query);
            if (variables != null) {
                payload.put("variables", variables);
            }

            log.debug("executeQuery: POST {} (payload length: {})", endpointUrl, payload.length());

            Request.Builder reqBuilder = new Request.Builder()
                    .url(endpointUrl)
                    .post(RequestBody.create(payload.toString(), JSON_MEDIA_TYPE))
                    .header("User-Agent", "Nova-StashDB-Player/1.0");

            if (!apiKey.isEmpty()) {
                reqBuilder.header("ApiKey", apiKey);
            }

            try (Response resp = mHttpClient.newCall(reqBuilder.build()).execute()) {
                if (!resp.isSuccessful()) {
                    String errBody = resp.body() != null ? resp.body().string() : "";
                    log.error("executeQuery: HTTP {} {} - Body: {}", resp.code(), resp.message(), errBody);
                    throw new IOException("StashDB HTTP error " + resp.code() + ": " + resp.message());
                }

                String body = resp.body() != null ? resp.body().string() : "{}";
                JSONObject json = new JSONObject(body);

                // Check for GraphQL errors
                JSONArray errors = json.optJSONArray("errors");
                if (errors != null && errors.length() > 0) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < errors.length(); i++) {
                        JSONObject err = errors.optJSONObject(i);
                        if (err != null) {
                            String msg = err.optString("message", "Unknown GraphQL error");
                            if (sb.length() > 0) sb.append(", ");
                            sb.append(msg);
                        }
                    }
                    String errSummary = sb.toString();
                    log.error("executeQuery: StashDB GraphQL error(s): {}", errSummary);
                    if (errSummary.toLowerCase().contains("not authorized") || errSummary.toLowerCase().contains("unauthorized")) {
                        throw new IOException("StashDB Authorization Failed: Check API Key in Settings (" + errSummary + ")");
                    }
                    throw new IOException("StashDB Error: " + errSummary);
                }

                return json;
            }
        } catch (JSONException e) {
            log.error("executeQuery: JSON parsing error: {}", e.getMessage(), e);
            throw new IOException("Invalid JSON response from StashDB", e);
        }
    }

    private StashScene parseScene(JSONObject json) {
        StashScene scene = new StashScene();
        scene.id = json.optString("id", null);
        scene.title = json.optString("title", "");
        scene.releaseDate = json.optString("release_date", null);
        scene.details = json.optString("details", "");
        scene.durationSec = json.optInt("duration", 0);

        JSONObject studioObj = json.optJSONObject("studio");
        if (studioObj != null) {
            scene.studio = new StashStudio();
            scene.studio.id = studioObj.optString("id", null);
            scene.studio.name = studioObj.optString("name", "Unknown Studio");
            scene.studio.imagePath = studioObj.optString("image_path", null);
        }

        JSONArray perfsArray = json.optJSONArray("performers");
        if (perfsArray != null) {
            for (int i = 0; i < perfsArray.length(); i++) {
                JSONObject pItem = perfsArray.optJSONObject(i);
                if (pItem != null) {
                    JSONObject perfObj = pItem.optJSONObject("performer");
                    if (perfObj != null) {
                        StashPerformer performer = new StashPerformer();
                        performer.id = perfObj.optString("id", null);
                        performer.name = perfObj.optString("name", "");
                        performer.gender = perfObj.optString("gender", "");
                        performer.disambiguation = perfObj.optString("disambiguation", "");
                        performer.imagePath = perfObj.optString("image_path", null);
                        scene.performers.add(performer);
                    }
                }
            }
        }

        JSONArray tagsArray = json.optJSONArray("tags");
        if (tagsArray != null) {
            for (int i = 0; i < tagsArray.length(); i++) {
                JSONObject tObj = tagsArray.optJSONObject(i);
                if (tObj != null) {
                    String name = tObj.optString("name", null);
                    if (name != null && !name.isEmpty()) {
                        scene.tags.add(name);
                    }
                }
            }
        }

        JSONArray imagesArray = json.optJSONArray("images");
        if (imagesArray != null) {
            for (int i = 0; i < imagesArray.length(); i++) {
                JSONObject imgObj = imagesArray.optJSONObject(i);
                if (imgObj != null) {
                    StashImage img = new StashImage();
                    img.id = imgObj.optString("id", null);
                    img.url = imgObj.optString("url", null);
                    img.width = imgObj.optInt("width", 0);
                    img.height = imgObj.optInt("height", 0);
                    if (img.url != null && !img.url.isEmpty()) {
                        scene.images.add(img);
                    }
                }
            }
        }

        return scene;
    }
}
