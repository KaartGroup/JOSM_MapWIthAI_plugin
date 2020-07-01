// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai;

import static org.openstreetmap.josm.gui.help.HelpUtil.ht;
import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.event.KeyEvent;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.swing.JMenuItem;

import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.actions.PreferencesAction;
import org.openstreetmap.josm.data.validation.OsmValidator;
import org.openstreetmap.josm.data.validation.Test;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MainMenu;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.download.DownloadDialog;
import org.openstreetmap.josm.gui.download.OSMDownloadSource;
import org.openstreetmap.josm.gui.preferences.PreferenceSetting;
import org.openstreetmap.josm.io.remotecontrol.RequestProcessor;
import org.openstreetmap.josm.plugins.Plugin;
import org.openstreetmap.josm.plugins.PluginInformation;
import org.openstreetmap.josm.plugins.mapwithai.backend.DownloadListener;
import org.openstreetmap.josm.plugins.mapwithai.backend.MapWithAIAction;
import org.openstreetmap.josm.plugins.mapwithai.backend.MapWithAIDataUtils;
import org.openstreetmap.josm.plugins.mapwithai.backend.MapWithAILayer;
import org.openstreetmap.josm.plugins.mapwithai.backend.MapWithAIMoveAction;
import org.openstreetmap.josm.plugins.mapwithai.backend.MapWithAIObject;
import org.openstreetmap.josm.plugins.mapwithai.backend.MapWithAIRemoteControl;
import org.openstreetmap.josm.plugins.mapwithai.backend.MapWithAIUploadHook;
import org.openstreetmap.josm.plugins.mapwithai.backend.MergeDuplicateWaysAction;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.PreConflatedDataUtils;
import org.openstreetmap.josm.plugins.mapwithai.data.validation.tests.ConnectingNodeInformationTest;
import org.openstreetmap.josm.plugins.mapwithai.data.validation.tests.RoutingIslandsTest;
import org.openstreetmap.josm.plugins.mapwithai.data.validation.tests.StreetAddressOrder;
import org.openstreetmap.josm.plugins.mapwithai.data.validation.tests.StreetAddressTest;
import org.openstreetmap.josm.plugins.mapwithai.data.validation.tests.StubEndsTest;
import org.openstreetmap.josm.plugins.mapwithai.gui.MapWithAIMenu;
import org.openstreetmap.josm.plugins.mapwithai.gui.download.MapWithAIDownloadOptions;
import org.openstreetmap.josm.plugins.mapwithai.gui.download.MapWithAIDownloadSourceType;
import org.openstreetmap.josm.plugins.mapwithai.gui.preferences.MapWithAIPreferences;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.Destroyable;
import org.openstreetmap.josm.tools.Logging;

public final class MapWithAIPlugin extends Plugin implements Destroyable {
    /** The name of the plugin */
    public static final String NAME = "MapWithAI";

    static final String PAINTSTYLE_PREEXISTS = NAME.concat(".paintstyleprexists");
    private static String versionInfo;

    private final PreferenceSetting preferenceSetting;

    private final List<Destroyable> destroyables;

    private final PreferencesAction preferenceAction;

    private final MapWithAIMenu mapwithaiMenu;

    private static final Map<Class<? extends JosmAction>, Boolean> MENU_ENTRIES = new LinkedHashMap<>();
    static {
        MENU_ENTRIES.put(MapWithAIAction.class, false);
        MENU_ENTRIES.put(MapWithAIMoveAction.class, false);
        MENU_ENTRIES.put(MergeDuplicateWaysAction.class, true);
    }

    private static final List<Class<? extends Test>> VALIDATORS = Arrays.asList(RoutingIslandsTest.class,
            ConnectingNodeInformationTest.class, StubEndsTest.class, StreetAddressTest.class, StreetAddressOrder.class);

    public MapWithAIPlugin(PluginInformation info) {
        super(info);

        preferenceSetting = new MapWithAIPreferences();

        // Add MapWithAI specific menu
        mapwithaiMenu = new MapWithAIMenu();
        MainApplication.getMenu().addMenu(mapwithaiMenu, "mapwithai:menu", KeyEvent.VK_M, 9, ht("/Plugin/MapWithAI"));

        for (final Map.Entry<Class<? extends JosmAction>, Boolean> entry : MENU_ENTRIES.entrySet()) {
            if (Arrays.asList(mapwithaiMenu.getMenuComponents()).parallelStream().filter(JMenuItem.class::isInstance)
                    .map(JMenuItem.class::cast)
                    .noneMatch(component -> entry.getKey().equals(component.getAction().getClass()))) {
                try {
                    MainMenu.add(mapwithaiMenu, entry.getKey().getDeclaredConstructor().newInstance(),
                            entry.getValue());
                } catch (InstantiationException | IllegalAccessException | IllegalArgumentException
                        | InvocationTargetException | NoSuchMethodException | SecurityException e) {
                    Logging.debug(e);
                }
            }
        }

        // Add the preferences last to data
        preferenceAction = PreferencesAction.forPreferenceTab(tr("MapWithAI Preferences"), tr("MapWithAI Preferences"),
                MapWithAIPreferences.class);
        MainMenu.add(mapwithaiMenu, preferenceAction);

        VALIDATORS.forEach(clazz -> {
            if (!OsmValidator.getAllAvailableTestClasses().contains(clazz)) {
                OsmValidator.addTest(clazz);
            }
        });

        if (!Config.getPref().getKeySet().contains(PAINTSTYLE_PREEXISTS)) {
            Config.getPref().putBoolean(PAINTSTYLE_PREEXISTS, MapWithAIDataUtils.checkIfMapWithAIPaintStyleExists());
        }

        MapWithAIDataUtils.addMapWithAIPaintStyles();

        destroyables = new ArrayList<>();
        MapWithAIDownloadOptions mapWithAIDownloadOptions = new MapWithAIDownloadOptions();
        mapWithAIDownloadOptions.addGui(DownloadDialog.getInstance());
        destroyables.add(mapWithAIDownloadOptions);

        setVersionInfo(info.localversion);
        RequestProcessor.addRequestHandlerClass("mapwithai", MapWithAIRemoteControl.class);
        new MapWithAIRemoteControl(); // instantiate to get action into Remote Control Preferences
        destroyables.add(new MapWithAIUploadHook(info));
        destroyables.add(new PreConflatedDataUtils());
        mapFrameInitialized(null, MainApplication.getMap());
        OSMDownloadSource.addDownloadType(new MapWithAIDownloadSourceType());
        MainApplication.worker.execute(() -> UpdateProd.doProd(info.mainversion));
    }

    @Override
    public void mapFrameInitialized(MapFrame oldFrame, MapFrame newFrame) {
        final Optional<MapWithAIObject> possibleMapWithAIObject = destroyables.parallelStream()
                .filter(MapWithAIObject.class::isInstance).map(MapWithAIObject.class::cast).findFirst();
        final MapWithAIObject mapWithAIObject = possibleMapWithAIObject.orElse(new MapWithAIObject());
        if ((oldFrame != null) && (oldFrame.statusLine != null)) {
            mapWithAIObject.removeMapStatus(oldFrame.statusLine);
        }
        if ((newFrame != null) && (newFrame.statusLine != null)) {
            mapWithAIObject.addMapStatus(newFrame.statusLine);
        }
        if (!destroyables.contains(mapWithAIObject)) {
            destroyables.add(mapWithAIObject);
        }
    }

    @Override
    public PreferenceSetting getPreferenceSetting() {
        return preferenceSetting;
    }

    /**
     * The current version for the plugin
     *
     * @return The version information of the plugin
     */
    public static String getVersionInfo() {
        return versionInfo;
    }

    private static void setVersionInfo(String newVersionInfo) {
        versionInfo = newVersionInfo;
    }

    /**
     * This is so that if JOSM ever decides to support updating plugins without
     * restarting, I don't have to do anything (hopefully -- I might have to change
     * the interface and method). Not currently used... (October 16, 2019)
     */
    @Override
    public void destroy() {
        MainApplication.getMenu().remove(this.mapwithaiMenu);

        MainApplication.getLayerManager().getLayersOfType(MapWithAILayer.class).stream()
                .forEach(layer -> MainApplication.getLayerManager().removeLayer(layer));

        if (!Config.getPref().getBoolean(PAINTSTYLE_PREEXISTS)) {
            MapWithAIDataUtils.removeMapWithAIPaintStyles();
        }

        destroyables.forEach(Destroyable::destroy);
        OSMDownloadSource.removeDownloadType(OSMDownloadSource.getDownloadType(MapWithAIDownloadSourceType.class));
        VALIDATORS.forEach(OsmValidator::removeTest);
        DownloadListener.destroyAll();
    }
}
