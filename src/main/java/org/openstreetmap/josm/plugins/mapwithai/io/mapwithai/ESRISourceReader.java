// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.io.mapwithai;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonString;
import javax.json.JsonStructure;
import javax.json.JsonValue;
import javax.json.stream.JsonParsingException;

import org.openstreetmap.josm.data.cache.JCSCacheManager;
import org.openstreetmap.josm.data.imagery.ImageryInfo.ImageryBounds;
import org.openstreetmap.josm.data.preferences.LongProperty;
import org.openstreetmap.josm.io.CachedFile;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAICategory;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAIInfo;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAIType;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.HttpClient;
import org.openstreetmap.josm.tools.Logging;

import org.apache.commons.jcs3.access.CacheAccess;
import org.apache.commons.jcs3.engine.behavior.IElementAttributes;

/**
 * Take a {@link MapWithAIInfo.MapWithAIType#ESRI} layer and convert it to a
 * list of "true" layers.
 */
public class ESRISourceReader {
    /** The cache storing ESRI source information (json) */
    public static final CacheAccess<String, String> SOURCE_CACHE = JCSCacheManager.getCache("mapwithai:esrisources", 5,
            50_000, new File(Config.getDirs().getCacheDirectory(true), "mapwithai").getPath());
    private final MapWithAIInfo source;
    private boolean fastFail;
    private final List<MapWithAICategory> ignoreConflationCategories;
    private static final String JSON_QUERY_PARAM = "?f=json";
    private static final LongProperty MIRROR_MAXTIME = new LongProperty("mirror.maxtime", TimeUnit.DAYS.toSeconds(7));

    /**
     * Constructs a {@code ImageryReader} from a given filename, URL or internal
     * resource.
     *
     * @param source can be:
     *               <ul>
     *               <li>relative or absolute file name</li>
     *               <li>{@code file:///SOME/FILE} the same as above</li>
     *               <li>{@code http://...} a URL. It will be cached on disk.</li>
     *               <li>{@code resource://SOME/FILE} file from the classpath
     *               (usually in the current *.jar)</li>
     *               <li>{@code josmdir://SOME/FILE} file inside josm user data
     *               directory (since r7058)</li>
     *               <li>{@code josmplugindir://SOME/FILE} file inside josm plugin
     *               directory (since r7834)</li>
     *               </ul>
     */
    public ESRISourceReader(MapWithAIInfo source) {
        this.source = source;
        this.ignoreConflationCategories = source.getConflationIgnoreCategory();
    }

    /**
     * Parses MapWithAI source.
     *
     * @return list of source info
     * @throws IOException if any I/O error occurs
     */
    public List<MapWithAIInfo> parse() throws IOException {
        Pattern startReplace = Pattern.compile("\\{start\\}");
        String search = "/search" + JSON_QUERY_PARAM + "&sortField=added&sortOrder=desc&num=12&start={start}";
        String url = source.getUrl();
        String group = source.getId();
        if (!url.endsWith("/")) {
            url = url.concat("/");
        }

        List<MapWithAIInfo> information = new ArrayList<>();

        String next = "1";
        String searchUrl = startReplace.matcher(search).replaceAll(next);
        while (!next.equals("-1")) {
            final String finalUrl = url + "content/groups/" + group + searchUrl;
            final String jsonString = getJsonString(finalUrl, TimeUnit.SECONDS.toMillis(MIRROR_MAXTIME.get()) / 7,
                    this.fastFail);
            if (jsonString == null) {
                continue;
            }
            try (JsonReader reader = Json
                    .createReader(new ByteArrayInputStream(jsonString.getBytes(StandardCharsets.UTF_8)))) {
                JsonStructure parser = reader.read();
                if (parser.getValueType() == JsonValue.ValueType.OBJECT) {
                    JsonObject obj = parser.asJsonObject();
                    next = obj.getString("nextStart", "-1");
                    if ("-1".equals(next)) {
                        next = Integer.toString(obj.getInt("nextStart", -1));
                    }
                    searchUrl = startReplace.matcher(search).replaceAll(next);
                    JsonArray features = obj.getJsonArray("results");
                    for (JsonObject feature : features.getValuesAs(JsonObject.class)) {
                        information.add(parse(feature));
                    }
                }
            } catch (ClassCastException e) {
                Logging.error(e);
                next = "-1";
            }
        }
        Comparator<MapWithAIInfo> comparator = (o1, o2) -> o1.getCategory().getDescription()
                .compareTo(o2.getCategory().getDescription());
        if (information != null) {
            information = information.stream().sorted(comparator).collect(Collectors.toList());
        }
        return information;
    }

    /**
     * Get the json string for a URL
     *
     * @param url      The URL to get
     * @param fastFail Fail fast (1 second)
     * @return The json string, or {@code null}.
     */
    @Nullable
    private static String getJsonString(@Nonnull final String url, final long defaultMaxAge, final boolean fastFail) {
        String jsonString = SOURCE_CACHE.get(url);
        // TODO FIXME remove sometime after January 2022 (give it a chance to cleanup
        // directories)
        CachedFile.cleanup(url);
        if (jsonString == null) {
            synchronized (SOURCE_CACHE) {
                jsonString = SOURCE_CACHE.get(url);
                if (jsonString == null) {
                    HttpClient client = null;
                    try {
                        client = HttpClient.create(new URL(url));
                        if (fastFail) {
                            client.setReadTimeout(1000);
                        }
                        final HttpClient.Response response = client.connect();
                        jsonString = response.fetchContent();
                        if (jsonString != null && response.getResponseCode() < 400
                                && response.getResponseCode() >= 200) {
                            final IElementAttributes elementAttributes = SOURCE_CACHE.getDefaultElementAttributes();
                            // getExpiration returns milliseconds
                            final long expirationTime = response.getExpiration();
                            if (expirationTime > 0) {
                                elementAttributes.setMaxLife(response.getExpiration());
                            } else {
                                elementAttributes.setMaxLife(defaultMaxAge);
                            }
                            SOURCE_CACHE.put(url, jsonString, elementAttributes);
                        } else {
                            jsonString = null;
                        }
                    } catch (final IOException e) {
                        Logging.error(e);
                    } finally {
                        if (client != null) {
                            client.disconnect();
                        }
                    }
                }
            }
        }
        return jsonString;
    }

    private MapWithAIInfo parse(JsonObject feature) {
        // Use the initial esri server information to keep conflation info
        MapWithAIInfo newInfo = new MapWithAIInfo(source);
        newInfo.setId(feature.getString("id"));
        if (feature.getString("type", "").equals("Feature Service")) {
            newInfo.setUrl(featureService(newInfo, feature.getString("url")));
        } else {
            newInfo.setUrl(feature.getString("url"));
        }
        newInfo.setName(feature.getString("title", feature.getString("name")));
        String[] extent = feature.getJsonArray("extent").getValuesAs(JsonArray.class).stream()
                .flatMap(array -> array.getValuesAs(JsonNumber.class).stream()).map(JsonNumber::doubleValue)
                .map(Object::toString).toArray(String[]::new);
        ImageryBounds imageryBounds = new ImageryBounds(String.join(",", extent[1], extent[0], extent[3], extent[2]),
                ",");
        newInfo.setBounds(imageryBounds);
        newInfo.setSourceType(MapWithAIType.ESRI_FEATURE_SERVER);
        newInfo.setTermsOfUseText(feature.getString("licenseInfo", null));
        if (feature.containsKey("thumbnail")) {
            newInfo.setAttributionImageURL(
                    source.getUrl() + "content/items/" + newInfo.getId() + "/info/" + feature.getString("thumbnail"));
        }
        if (feature.containsKey("groupCategories")) {
            List<MapWithAICategory> categories = feature.getJsonArray("groupCategories").getValuesAs(JsonString.class)
                    .stream().map(JsonString::getString).map(s -> s.replace("/Categories/", ""))
                    .map(MapWithAICategory::fromString).filter(Objects::nonNull)
                    .collect(Collectors.toCollection(ArrayList::new));
            MapWithAICategory category = categories.stream().filter(c -> MapWithAICategory.FEATURED != c).findFirst()
                    .orElse(MapWithAICategory.OTHER);
            newInfo.setCategory(category);
            categories.remove(category);
            newInfo.setAdditionalCategories(categories);
        }

        if (this.ignoreConflationCategories.contains(newInfo.getCategory())) {
            newInfo.setConflation(false);
        }
        if (feature.containsKey("accessInformation")) {
            newInfo.setAttributionText(feature.getString("accessInformation"));
        }
        newInfo.setDescription(feature.getString("snippet"));
        if (newInfo.getSource() != null) {
            StringBuilder sourceTag = new StringBuilder(newInfo.getSource());
            if (!sourceTag.toString().endsWith("/")) {
                sourceTag.append('/');
            }
            sourceTag.append(feature.getString("name", newInfo.getId()));
            newInfo.setSource(sourceTag.toString());
        }
        newInfo.setTermsOfUseURL("https://wiki.openstreetmap.org/wiki/Esri/ArcGIS_Datasets#License");
        return (newInfo);
    }

    /**
     * Get feature service information
     *
     * @param mapwithaiInfo The info to update
     * @param url           The url to get
     * @return The base url for the feature service
     */
    @Nullable
    private String featureService(@Nonnull MapWithAIInfo mapwithaiInfo, @Nonnull String url) {
        final String toGet = url.endsWith(JSON_QUERY_PARAM) ? url : url.concat(JSON_QUERY_PARAM);
        final String jsonString = getJsonString(toGet, TimeUnit.SECONDS.toMillis(MIRROR_MAXTIME.get()), this.fastFail);
        if (jsonString == null) {
            return null;
        }

        try (JsonReader reader = Json
                .createReader(new ByteArrayInputStream(jsonString.getBytes(StandardCharsets.UTF_8)))) {
            JsonObject info = reader.readObject();
            JsonArray layers = info.getJsonArray("layers");
            // This fixes #20551
            if (layers == null || layers.stream().noneMatch(Objects::nonNull)) {
                return null;
            }
            // TODO use all the layers?
            JsonObject layer = layers.stream().filter(Objects::nonNull).findFirst().orElse(JsonValue.EMPTY_JSON_OBJECT)
                    .asJsonObject();
            if (layer.containsKey("id")) {
                String partialUrl = (url.endsWith("/") ? url : url + "/") + layer.getInt("id");
                mapwithaiInfo.setReplacementTags(getReplacementTags(partialUrl));

                return partialUrl;
            }
        } catch (JsonParsingException e) {
            Logging.error(e);
        }
        return null;
    }

    /**
     * Get the replacement tags for a feature service
     *
     * @param layerUrl The service url
     * @return A map of replacement tags
     */
    @Nonnull
    private Map<String, String> getReplacementTags(@Nonnull String layerUrl) {
        String toGet = layerUrl.endsWith(JSON_QUERY_PARAM) ? layerUrl : layerUrl.concat(JSON_QUERY_PARAM);
        final String jsonString = getJsonString(toGet, TimeUnit.SECONDS.toMillis(MIRROR_MAXTIME.get()), this.fastFail);
        if (jsonString != null) {
            try (JsonReader reader = Json
                    .createReader(new ByteArrayInputStream(jsonString.getBytes(StandardCharsets.UTF_8)))) {
                return reader.readObject().getJsonArray("fields").getValuesAs(JsonObject.class).stream()
                        .collect(Collectors.toMap(o -> o.getString("name"), ESRISourceReader::getReplacementTag));
            }
        }
        return Collections.emptyMap();
    }

    private static String getReplacementTag(JsonObject tag) {
        if (tag.getBoolean("editable", true) && !"esriFieldTypeOID".equals(tag.getString("type", null))) {
            return tag.getString("alias", "");
        }
        return "";
    }

    /**
     * Sets whether opening HTTP connections should fail fast, i.e., whether a
     * {@link HttpClient#setConnectTimeout(int) low connect timeout} should be used.
     *
     * @param fastFail whether opening HTTP connections should fail fast
     * @see CachedFile#setFastFail(boolean)
     */
    public void setFastFail(boolean fastFail) {
        this.fastFail = fastFail;
    }
}
