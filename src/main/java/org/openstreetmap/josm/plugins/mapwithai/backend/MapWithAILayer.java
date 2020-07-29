// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.backend;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

import org.openstreetmap.josm.actions.ExpertToggleAction;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.DownloadPolicy;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.UploadPolicy;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.gui.layer.MainLayerManager.ActiveLayerChangeEvent;
import org.openstreetmap.josm.gui.layer.MainLayerManager.ActiveLayerChangeListener;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.gui.mappaint.MapPaintStyles;
import org.openstreetmap.josm.gui.mappaint.StyleSource;
import org.openstreetmap.josm.gui.util.GuiHelper;
import org.openstreetmap.josm.plugins.mapwithai.MapWithAIPlugin;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAIInfo;
import org.openstreetmap.josm.plugins.mapwithai.tools.MapPaintUtils;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.GBC;
import org.openstreetmap.josm.tools.ImageProvider;

/**
 * This layer shows MapWithAI data. For various reasons, we currently only allow
 * one to be created, although this may change.
 *
 * @author Taylor Smock
 *
 */
public class MapWithAILayer extends OsmDataLayer implements ActiveLayerChangeListener {
    private Integer maximumAddition;
    private MapWithAIInfo url;
    private Boolean switchLayers;
    private boolean continuousDownload = true;
    private final Lock lock;
    private final HashSet<MapWithAIInfo> downloadedInfo = new HashSet<>();

    /**
     * Create a new MapWithAI layer
     *
     * @param data           OSM data from MapWithAI
     * @param name           Layer name
     * @param associatedFile an associated file (can be null)
     */
    public MapWithAILayer(DataSet data, String name, File associatedFile) {
        super(data, name, associatedFile);
        data.setUploadPolicy(UploadPolicy.BLOCKED);
        data.setDownloadPolicy(DownloadPolicy.BLOCKED);
        lock = new MapLock();
        MainApplication.getLayerManager().addActiveLayerChangeListener(this);
    }

    @Override
    public String getChangesetSourceTag() {
        return MapWithAIDataUtils.getAddedObjects() > 0 ? String.join("; ", MapWithAIDataUtils.getAddedObjectsSource())
                : null;
    }

    public void setMaximumAddition(Integer max) {
        maximumAddition = max;
    }

    public Integer getMaximumAddition() {
        return maximumAddition;
    }

    public void setMapWithAIUrl(MapWithAIInfo info) {
        this.url = info;
    }

    public MapWithAIInfo getMapWithAIUrl() {
        return url;
    }

    public void setSwitchLayers(boolean selected) {
        switchLayers = selected;
    }

    public Boolean isSwitchLayers() {
        return switchLayers;
    }

    @Override
    public Object getInfoComponent() {
        final Object p = super.getInfoComponent();
        if (p instanceof JPanel) {
            final JPanel panel = (JPanel) p;
            if (maximumAddition != null) {
                panel.add(new JLabel(tr("Maximum Additions: {0}", maximumAddition), SwingConstants.HORIZONTAL),
                        GBC.eop().insets(15, 0, 0, 0));
            }
            if (url != null) {
                panel.add(new JLabel(tr("URL: {0}", url.getUrlExpanded()), SwingConstants.HORIZONTAL),
                        GBC.eop().insets(15, 0, 0, 0));
            }
            if (switchLayers != null) {
                panel.add(new JLabel(tr("Switch Layers: {0}", switchLayers), SwingConstants.HORIZONTAL),
                        GBC.eop().insets(15, 0, 0, 0));
            }
        }
        return p;
    }

    @Override
    public Action[] getMenuEntries() {
        final List<Action> actions = Arrays.asList(super.getMenuEntries()).stream()
                .filter(action -> !(action instanceof LayerSaveAction) && !(action instanceof LayerSaveAsAction))
                .collect(Collectors.toCollection(ArrayList::new));
        if (actions.isEmpty()) {
            actions.add(new ContinuousDownloadAction(this));
        } else {
            actions.add(actions.size() - 2, new ContinuousDownloadAction(this));
        }
        return actions.toArray(new Action[0]);
    }

    public Lock getLock() {
        return lock;
    }

    private class MapLock extends ReentrantLock {
        private static final long serialVersionUID = 5441350396443132682L;
        private boolean dataSetLocked;

        public MapLock() {
            super();
            // Do nothing
        }

        @Override
        public void lock() {
            super.lock();
            dataSetLocked = getDataSet().isLocked();
            if (dataSetLocked) {
                getDataSet().unlock();
            }
        }

        @Override
        public void unlock() {
            super.unlock();
            if (dataSetLocked) {
                getDataSet().lock();
            }
        }
    }

    @Override
    public void activeOrEditLayerChanged(ActiveLayerChangeEvent e) {
        if (checkIfToggleLayer()) {
            final StyleSource style = MapPaintUtils.getMapWithAIPaintStyle();
            if (style.active != this.equals(MainApplication.getLayerManager().getActiveLayer())) {
                MapPaintStyles.toggleStyleActive(MapPaintStyles.getStyles().getStyleSources().indexOf(style));
            }
        }
    }

    private static boolean checkIfToggleLayer() {
        final List<String> keys = Config.getPref().getKeySet().parallelStream()
                .filter(string -> string.contains(MapWithAIPlugin.NAME) && string.contains("boolean:toggle_with_layer"))
                .collect(Collectors.toList());
        boolean toggle = false;
        if (keys.size() == 1) {
            toggle = Config.getPref().getBoolean(keys.get(0), false);
        }
        return toggle;
    }

    @Override
    public synchronized void destroy() {
        super.destroy();
        MainApplication.getLayerManager().removeActiveLayerChangeListener(this);
    }

    @Override
    public Icon getIcon() {
        return ImageProvider.getIfAvailable("mapwithai") == null ? super.getIcon()
                : ImageProvider.get("mapwithai", ImageProvider.ImageSizes.LAYER);
    }

    /**
     * Call after download from server
     *
     * @param bounds The newly added bounds
     */
    public void onPostDownloadFromServer(Bounds bounds) {
        super.onPostDownloadFromServer();
        GetDataRunnable.cleanup(getDataSet(), bounds, null);
    }

    @Override
    public void selectionChanged(SelectionChangeEvent event) {
        super.selectionChanged(event);
        final int maximumAdditionSelection = MapWithAIPreferenceHelper.getMaximumAddition();
        if ((event.getSelection().size() - event.getOldSelection().size() > 1
                || maximumAdditionSelection < event.getSelection().size())
                && (MapWithAIPreferenceHelper.getMaximumAddition() != 0 || !ExpertToggleAction.isExpert())) {
            Collection<OsmPrimitive> selection;
            if (event.getSelection().parallelStream().filter(Node.class::isInstance).map(Node.class::cast)
                    .filter(n -> !event.getOldSelection().contains(n))
                    .allMatch(n -> event.getOldSelection().parallelStream().filter(Way.class::isInstance)
                            .map(Way.class::cast).anyMatch(w -> w.containsNode(n)))) {
                selection = event.getSelection();
            } else {
                OsmComparator comparator = new OsmComparator(event.getOldSelection());
                selection = event.getSelection().stream().distinct().sorted(comparator).limit(maximumAdditionSelection)
                        .limit(event.getOldSelection().size() + Math.max(1L, maximumAdditionSelection / 10L))
                        .collect(Collectors.toList());
            }
            GuiHelper.runInEDT(() -> getDataSet().setSelected(selection));
        }
    }

    private static class OsmComparator implements Comparator<OsmPrimitive>, Serializable {
        final Collection<OsmPrimitive> previousSelection;

        public OsmComparator(Collection<OsmPrimitive> previousSelection) {
            this.previousSelection = previousSelection;
        }

        @Override
        public int compare(OsmPrimitive o1, OsmPrimitive o2) {
            if (previousSelection.contains(o1) == previousSelection.contains(o2)) {
                if (o1.isTagged() == o2.isTagged()) {
                    return o1.compareTo(o2);
                } else if (o1.isTagged()) {
                    return -1;
                }
                return 1;
            }
            if (previousSelection.contains(o1)) {
                return -1;
            }
            return 1;
        }

    }

    /**
     * Check if we want to download data continuously
     *
     * @return {@code true} indicates that we should attempt to keep it in sync with
     *         the data layer(s)
     */
    public boolean downloadContinuous() {
        return continuousDownload;
    }

    /**
     * Allow continuous download of data (for the layer that MapWithAI is clamped
     * to).
     *
     * @author Taylor Smock
     */
    public static class ContinuousDownloadAction extends AbstractAction implements LayerAction {
        private static final long serialVersionUID = -3528632887550700527L;
        private final transient MapWithAILayer layer;

        public ContinuousDownloadAction(MapWithAILayer layer) {
            super(tr("Continuous download"));
            new ImageProvider("download").getResource().attachImageIcon(this, true);
            this.layer = layer;
            updateListeners();
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            layer.continuousDownload = !layer.continuousDownload;
            updateListeners();
        }

        void updateListeners() {
            if (layer.continuousDownload) {
                for (OsmDataLayer data : MainApplication.getLayerManager().getLayersOfType(OsmDataLayer.class)) {
                    if (!(data instanceof MapWithAILayer)) {
                        new DownloadListener(data.getDataSet());
                    }
                }
            } else {
                DownloadListener.destroyAll();
            }
        }

        @Override
        public boolean supportLayers(List<Layer> layers) {
            return layers.stream().allMatch(MapWithAILayer.class::isInstance);
        }

        @Override
        public Component createMenuComponent() {
            JCheckBoxMenuItem item = new JCheckBoxMenuItem(this);
            item.setSelected(layer.continuousDownload);
            return item;
        }

    }

    /**
     * Check if the layer has downloaded a specific data type
     *
     * @param info The info to check
     * @return {@code true} if the info has been added to the layer
     */
    public boolean hasDownloaded(MapWithAIInfo info) {
        return downloadedInfo.contains(info);
    }

    /**
     * Indicate an info has been downloaded in this layer
     *
     * @param info The info that has been downloaded
     */
    public void addDownloadedInfo(MapWithAIInfo info) {
        downloadedInfo.add(info);
    }

    /**
     * Get the info that has been downloaded into this layer
     *
     * @return An unmodifiable collection of the downloaded info
     */
    public Collection<MapWithAIInfo> getDownloadedInfo() {
        return Collections.unmodifiableCollection(downloadedInfo);
    }

    @Override
    public boolean autosave(File file) throws IOException {
        // Consider a deletetion a "successful" save.
        return file.delete();
    }
}
