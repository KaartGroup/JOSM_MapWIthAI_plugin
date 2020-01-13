// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.backend;

import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.stream.Collectors;

import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.DataSourceChangeEvent;
import org.openstreetmap.josm.data.osm.DataSourceListener;
import org.openstreetmap.josm.data.osm.event.DataSourceAddedEvent;
import org.openstreetmap.josm.tools.Destroyable;

/**
 * This class listens for download events, and then gets new data
 *
 * @author Taylor Smock
 *
 */
public final class DownloadListener implements DataSourceListener, Destroyable {

    private WeakReference<DataSet> ds;
    private static final Collection<DownloadListener> LISTENERS = new HashSet<>();

    public DownloadListener(DataSet dataSet) {
        ds = new WeakReference<>(Objects.requireNonNull(dataSet, "DataSet cannot be null"));
        ds.get().addDataSourceListener(this);
        LISTENERS.add(this);
    }

    @Override
    public void dataSourceChange(DataSourceChangeEvent event) {
        if (event instanceof DataSourceAddedEvent) {
            MapWithAILayer layer = MapWithAIDataUtils.getLayer(false);
            if (layer == null) {
                destroy();
                return;
            }
            if (layer.downloadContinuous()) {
                MapWithAIDataUtils.getMapWithAIData(layer, event.getAdded().stream().map(ev -> ev.bounds)
                        .map(Bounds::toBBox).collect(Collectors.toList()));
            }
        }
    }

    @Override
    public void destroy() {
        if (ds.get() != null) {
            // Should be added, so no exception should be thrown
            ds.get().removeDataSourceListener(this);
            ds.clear();
            LISTENERS.remove(this);
        }
    }

    /**
     * Destroy all download listeners for MapWithAI
     */
    public static void destroyAll() {
        LISTENERS.forEach(DownloadListener::destroy);
    }
}