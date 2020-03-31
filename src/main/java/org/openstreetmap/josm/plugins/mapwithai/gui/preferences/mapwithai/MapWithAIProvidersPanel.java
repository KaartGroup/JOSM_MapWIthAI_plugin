// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.gui.preferences.mapwithai;

import static org.openstreetmap.josm.tools.I18n.marktr;
import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.swing.AbstractAction;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JToolBar;
import javax.swing.UIManager;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumnModel;

import org.openstreetmap.gui.jmapviewer.Coordinate;
import org.openstreetmap.gui.jmapviewer.MapPolygonImpl;
import org.openstreetmap.gui.jmapviewer.MapRectangleImpl;
import org.openstreetmap.gui.jmapviewer.interfaces.MapPolygon;
import org.openstreetmap.gui.jmapviewer.interfaces.MapRectangle;
import org.openstreetmap.josm.data.imagery.ImageryInfo;
import org.openstreetmap.josm.data.imagery.ImageryInfo.ImageryBounds;
import org.openstreetmap.josm.data.imagery.Shape;
import org.openstreetmap.josm.data.preferences.NamedColorProperty;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.bbox.JosmMapViewer;
import org.openstreetmap.josm.gui.bbox.SlippyMapBBoxChooser;
import org.openstreetmap.josm.gui.preferences.PreferenceTabbedPane;
import org.openstreetmap.josm.gui.preferences.imagery.ImageryProvidersPanel;
import org.openstreetmap.josm.gui.util.GuiHelper;
import org.openstreetmap.josm.gui.widgets.FilterField;
import org.openstreetmap.josm.gui.widgets.HtmlPanel;
import org.openstreetmap.josm.gui.widgets.JosmEditorPane;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAIInfo;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAIInfo.MapWithAICategory;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAILayerInfo;
import org.openstreetmap.josm.tools.GBC;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.ImageProvider.ImageSizes;
import org.openstreetmap.josm.tools.LanguageInfo;
import org.openstreetmap.josm.tools.Logging;

/**
 * A panel displaying imagery providers. Largely duplicates
 * {@link ImageryProvidersPanel}.
 *
 * @since 15115 (extracted from ImageryPreferences)
 */
public class MapWithAIProvidersPanel extends JPanel {
    private static final long serialVersionUID = -5876039771496409422L;
    // Public JTables and JosmMapViewer
    /** The table of active providers **/
    public final JTable activeTable;
    /** The table of default providers **/
    public final JTable defaultTable;
    /** The filter of default providers **/
    private final FilterField defaultFilter;
    /**
     * The selection listener synchronizing map display with table of default
     * providers
     **/
    private final transient DefListSelectionListener defaultTableListener;
    /** The map displaying imagery bounds of selected default providers **/
    public final JosmMapViewer defaultMap;

    // Public models
    /** The model of active providers **/
    public final MapWithAILayerTableModel activeModel;
    /** The model of default providers **/
    public final MapWithAIDefaultLayerTableModel defaultModel;

    // Public JToolbars
    /** The toolbar on the right of active providers **/
    public final JToolBar activeToolbar;
    /** The toolbar on the middle of the panel **/
    public final JToolBar middleToolbar;
    /** The toolbar on the right of default providers **/
    public final JToolBar defaultToolbar;

    // Private members
    private final PreferenceTabbedPane gui;
    private final transient MapWithAILayerInfo layerInfo;

    /**
     * class to render the URL information of MapWithAI source
     *
     * @since 8065
     */
    private static class MapWithAIURLTableCellRenderer extends DefaultTableCellRenderer {
        private static final long serialVersionUID = 184934756853356357L;

        private static final NamedColorProperty IMAGERY_BACKGROUND_COLOR = new NamedColorProperty(
                marktr("MapWithAI Background: Default"), new Color(200, 255, 200));

        private final transient List<MapWithAIInfo> layers;

        MapWithAIURLTableCellRenderer(List<MapWithAIInfo> layers) {
            this.layers = layers;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
                int row, int column) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row,
                    column);
            GuiHelper.setBackgroundReadable(label, UIManager.getColor("Table.background"));
            if (value != null) { // Fix #8159
                String t = value.toString();
                for (MapWithAIInfo l : layers) {
                    if (l.getUrl().equals(t)) {
                        GuiHelper.setBackgroundReadable(label, IMAGERY_BACKGROUND_COLOR.get());
                        break;
                    }
                }
                label.setToolTipText((String) value);
            }
            return label;
        }
    }

    /**
     * class to render an information of MapWithAI source
     *
     * @param <T> type of information
     */
    private static class MapWithAITableCellRenderer<T> extends DefaultTableCellRenderer {
        private static final long serialVersionUID = 1739280307217707613L;
        private final Function<T, Object> mapper;
        private final Function<T, String> tooltip;
        private final BiConsumer<T, JLabel> decorator;

        MapWithAITableCellRenderer(Function<T, Object> mapper, Function<T, String> tooltip,
                BiConsumer<T, JLabel> decorator) {
            this.mapper = mapper;
            this.tooltip = tooltip;
            this.decorator = decorator;
        }

        @Override
        @SuppressWarnings("unchecked")
        public final Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                boolean hasFocus, int row, int column) {
            T obj = (T) value;
            JLabel label = (JLabel) super.getTableCellRendererComponent(table, mapper.apply(obj), isSelected, hasFocus,
                    row, column);
            GuiHelper.setBackgroundReadable(label, isSelected ? UIManager.getColor("Table.selectionBackground")
                    : UIManager.getColor("Table.background"));
            if (obj != null) {
                label.setToolTipText(tooltip.apply(obj));
                if (decorator != null) {
                    decorator.accept(obj, label);
                }
            }
            return label;
        }
    }

    /**
     * class to render the category information of MapWithAI source
     */
    private static class MapWithAICategoryTableCellRenderer
            extends MapWithAIProvidersPanel.MapWithAITableCellRenderer<MapWithAICategory> {
        private static final long serialVersionUID = -6729184413815746598L;

        MapWithAICategoryTableCellRenderer() {
            super(cat -> null, cat -> tr("MapWithAI category: {0}", cat.getDescription()),
                    (cat, label) -> label.setIcon(cat.getIcon(ImageSizes.TABLE)));
        }
    }

    /**
     * class to render the country information of MapWithAI source
     */
    private static class MapWithAICountryTableCellRenderer
            extends MapWithAIProvidersPanel.MapWithAITableCellRenderer<String> {
        private static final long serialVersionUID = 5975643008500799758L;

        MapWithAICountryTableCellRenderer() {
            super(code -> code, ImageryInfo::getLocalizedCountry, null);
        }
    }

    /**
     * class to render the name information of Imagery source
     */
    private static class MapWithAINameTableCellRenderer
            extends MapWithAIProvidersPanel.MapWithAITableCellRenderer<MapWithAIInfo> {
        private static final long serialVersionUID = 6669934435517244629L;

        MapWithAINameTableCellRenderer() {
            super(info -> info == null ? null : info.getName(), MapWithAIInfo::getToolTipText, null);
        }
    }

    /**
     * Constructs a new {@code MapWithAIProvidersPanel}.
     *
     * @param gui          The parent preference tab pane
     * @param layerInfoArg The list of imagery entries to display
     */
    public MapWithAIProvidersPanel(final PreferenceTabbedPane gui, MapWithAILayerInfo layerInfoArg) {
        super(new GridBagLayout());
        this.gui = gui;
        this.layerInfo = layerInfoArg;
        this.activeModel = new MapWithAILayerTableModel();

        activeTable = new JTable(activeModel) {
            private static final long serialVersionUID = -6136421378119093719L;

            @Override
            public String getToolTipText(MouseEvent e) {
                java.awt.Point p = e.getPoint();
                try {
                    return activeModel.getValueAt(rowAtPoint(p), columnAtPoint(p)).toString();
                } catch (ArrayIndexOutOfBoundsException ex) {
                    Logging.debug(ex);
                    return null;
                }
            }
        };
        activeTable.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);

        defaultModel = new MapWithAIDefaultLayerTableModel();
        defaultTable = new JTable(defaultModel);
        defaultTable.setAutoCreateRowSorter(true);
        defaultFilter = new FilterField().filter(defaultTable, defaultModel);

        defaultModel.addTableModelListener(e -> activeTable.repaint());
        activeModel.addTableModelListener(e -> defaultTable.repaint());

        TableColumnModel mod = defaultTable.getColumnModel();
        mod.getColumn(3).setPreferredWidth(775);
        mod.getColumn(3).setCellRenderer(new MapWithAIURLTableCellRenderer(layerInfo.getLayers()));
        mod.getColumn(2).setPreferredWidth(475);
        mod.getColumn(2).setCellRenderer(new MapWithAINameTableCellRenderer());
        mod.getColumn(1).setCellRenderer(new MapWithAICountryTableCellRenderer());
        mod.getColumn(0).setPreferredWidth(50);
        mod.getColumn(0).setCellRenderer(new MapWithAICategoryTableCellRenderer());
        mod.getColumn(0).setPreferredWidth(50);

        mod = activeTable.getColumnModel();
        mod.getColumn(1).setPreferredWidth(800);
        mod.getColumn(1).setCellRenderer(new MapWithAIURLTableCellRenderer(layerInfo.getAllDefaultLayers()));
        mod.getColumn(0).setPreferredWidth(200);

        RemoveEntryAction remove = new RemoveEntryAction();
        activeTable.getSelectionModel().addListSelectionListener(remove);

        add(new JLabel(tr("Available default entries:")), GBC.std().insets(5, 5, 0, 0));
        add(new JLabel(tr("Boundaries of selected MapWithAI entries:")), GBC.eol().insets(5, 5, 0, 0));

        // Add default item list
        JPanel defaultPane = new JPanel(new GridBagLayout());
        JScrollPane scrolldef = new JScrollPane(defaultTable);
        scrolldef.setPreferredSize(new Dimension(200, 200));
        defaultPane.add(defaultFilter, GBC.eol().insets(0, 0, 0, 0).fill(GridBagConstraints.HORIZONTAL));
        defaultPane.add(scrolldef, GBC.eol().insets(0, 0, 0, 0).fill(GridBagConstraints.BOTH));
        add(defaultPane, GBC.std().fill(GridBagConstraints.BOTH).weight(1.0, 0.6).insets(5, 0, 0, 0));

        // Add default item map
        defaultMap = new JosmMapViewer();
        defaultMap.setTileSource(SlippyMapBBoxChooser.DefaultOsmTileSourceProvider.get()); // for attribution
        defaultMap.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1) {
                    defaultMap.getAttribution().handleAttribution(e.getPoint(), true);
                }
            }
        });
        defaultMap.setZoomControlsVisible(false);
        defaultMap.setMinimumSize(new Dimension(100, 200));
        add(defaultMap, GBC.std().fill(GridBagConstraints.BOTH).weight(0.33, 0.6).insets(5, 0, 0, 0));

        defaultTableListener = new DefListSelectionListener();
        defaultTable.getSelectionModel().addListSelectionListener(defaultTableListener);

        defaultToolbar = new JToolBar(JToolBar.VERTICAL);
        defaultToolbar.setFloatable(false);
        defaultToolbar.setBorderPainted(false);
        defaultToolbar.setOpaque(false);
        defaultToolbar.add(new ReloadAction());
        add(defaultToolbar, GBC.eol().anchor(GBC.SOUTH).insets(0, 0, 5, 0));

        HtmlPanel help = new HtmlPanel(
                tr("New default entries can be added in the <a href=\"{0}\">GitLab Repository</a>.",
                        "https://gitlab.com/gokaart/JOSM_MapWithAI/-/blob/pages/public/json/sources.json"));
        help.enableClickableHyperlinks();
        add(help, GBC.eol().insets(10, 0, 0, 0).fill(GBC.HORIZONTAL));

        ActivateAction activate = new ActivateAction();
        defaultTable.getSelectionModel().addListSelectionListener(activate);
        JButton btnActivate = new JButton(activate);

        middleToolbar = new JToolBar(JToolBar.HORIZONTAL);
        middleToolbar.setFloatable(false);
        middleToolbar.setBorderPainted(false);
        middleToolbar.setOpaque(false);
        middleToolbar.add(btnActivate);
        add(middleToolbar, GBC.eol().anchor(GBC.CENTER).insets(5, 5, 5, 0));

        add(Box.createHorizontalGlue(), GBC.eol().fill(GridBagConstraints.HORIZONTAL));

        add(new JLabel(tr("Selected entries:")), GBC.eol().insets(5, 0, 0, 0));
        JScrollPane scroll = new JScrollPane(activeTable);
        add(scroll, GBC.std().fill(GridBagConstraints.BOTH).span(GridBagConstraints.RELATIVE).weight(1.0, 0.4).insets(5,
                0, 0, 5));
        scroll.setPreferredSize(new Dimension(200, 200));

        activeToolbar = new JToolBar(JToolBar.VERTICAL);
        activeToolbar.setFloatable(false);
        activeToolbar.setBorderPainted(false);
        activeToolbar.setOpaque(false);
        activeToolbar.add(new NewEntryAction(MapWithAIInfo.MapWithAIType.THIRD_PARTY));
        activeToolbar.add(remove);
        add(activeToolbar, GBC.eol().anchor(GBC.NORTH).insets(0, 0, 5, 5));
    }

    // Listener of default providers list selection
    private final class DefListSelectionListener implements ListSelectionListener {
        // The current drawn rectangles and polygons
        private final Map<Integer, MapRectangle> mapRectangles;
        private final Map<Integer, List<MapPolygon>> mapPolygons;

        private DefListSelectionListener() {
            this.mapRectangles = new HashMap<>();
            this.mapPolygons = new HashMap<>();
        }

        private void clearMap() {
            defaultMap.removeAllMapRectangles();
            defaultMap.removeAllMapPolygons();
            mapRectangles.clear();
            mapPolygons.clear();
        }

        @Override
        public void valueChanged(ListSelectionEvent e) {
            // First index can be set to -1 when the list is refreshed, so discard all map
            // rectangles and polygons
            if (e.getFirstIndex() == -1) {
                clearMap();
            } else if (!e.getValueIsAdjusting()) {
                // Only process complete (final) selection events
                for (int i = e.getFirstIndex(); i <= e.getLastIndex(); i++) {
                    if (i < defaultTable.getRowCount()) {
                        updateBoundsAndShapes(defaultTable.convertRowIndexToModel(i));
                    }
                }
                // Cleanup residual selected bounds which may have disappeared after a filter
                cleanupResidualBounds();
                // If needed, adjust map to show all map rectangles and polygons
                if (!mapRectangles.isEmpty() || !mapPolygons.isEmpty()) {
                    defaultMap.setDisplayToFitMapElements(false, true, true);
                    defaultMap.zoomOut();
                }
            }
        }

        /**
         * update bounds and shapes for a new entry
         *
         * @param i model index
         */
        private void updateBoundsAndShapes(int i) {
            ImageryBounds bounds = defaultModel.getRow(i).getBounds();
            if (bounds != null) {
                int viewIndex = defaultTable.convertRowIndexToView(i);
                List<Shape> shapes = bounds.getShapes();
                if (shapes != null && !shapes.isEmpty()) {
                    if (defaultTable.getSelectionModel().isSelectedIndex(viewIndex)) {
                        if (!mapPolygons.containsKey(i)) {
                            List<MapPolygon> list = new ArrayList<>();
                            mapPolygons.put(i, list);
                            // Add new map polygons
                            for (Shape shape : shapes) {
                                MapPolygon polygon = new MapPolygonImpl(shape.getPoints());
                                list.add(polygon);
                                defaultMap.addMapPolygon(polygon);
                            }
                        }
                    } else if (mapPolygons.containsKey(i)) {
                        // Remove previously drawn map polygons
                        for (MapPolygon polygon : mapPolygons.get(i)) {
                            defaultMap.removeMapPolygon(polygon);
                        }
                        mapPolygons.remove(i);
                    }
                    // Only display bounds when no polygons (shapes) are defined for this provider
                } else {
                    if (defaultTable.getSelectionModel().isSelectedIndex(viewIndex)) {
                        if (!mapRectangles.containsKey(i)) {
                            // Add new map rectangle
                            Coordinate topLeft = new Coordinate(bounds.getMaxLat(), bounds.getMinLon());
                            Coordinate bottomRight = new Coordinate(bounds.getMinLat(), bounds.getMaxLon());
                            MapRectangle rectangle = new MapRectangleImpl(topLeft, bottomRight);
                            mapRectangles.put(i, rectangle);
                            defaultMap.addMapRectangle(rectangle);
                        }
                    } else if (mapRectangles.containsKey(i)) {
                        // Remove previously drawn map rectangle
                        defaultMap.removeMapRectangle(mapRectangles.get(i));
                        mapRectangles.remove(i);
                    }
                }
            }
        }

        private <T> void doCleanupResidualBounds(Map<Integer, T> map, Consumer<T> removalEffect) {
            for (Integer i : map.keySet()) {
                int viewIndex = defaultTable.convertRowIndexToView(i);
                if (!defaultTable.getSelectionModel().isSelectedIndex(viewIndex)) {
                    removalEffect.accept(map.remove(i));
                }
            }
        }

        private void cleanupResidualBounds() {
            doCleanupResidualBounds(mapPolygons, l -> l.forEach(defaultMap::removeMapPolygon));
            doCleanupResidualBounds(mapRectangles, defaultMap::removeMapRectangle);
        }
    }

    private class NewEntryAction extends AbstractAction {
        private static final long serialVersionUID = 7451336680150337942L;
        private final MapWithAIInfo.MapWithAIType type;

        NewEntryAction(MapWithAIInfo.MapWithAIType type) {
            putValue(NAME, type.toString());
            putValue(SHORT_DESCRIPTION, tr("Add a new {0} entry by entering the URL", type.toString()));
            String icon = /* ICON(dialogs/) */ "add";
            new ImageProvider("dialogs", icon).getResource().attachImageIcon(this, true);
            this.type = type;
        }

        @Override
        public void actionPerformed(ActionEvent evt) {
            final AddMapWithAIPanel p = new AddMapWithAIPanel();
            final AddMapWithAIDialog addDialog = new AddMapWithAIDialog(gui, p);
            addDialog.showDialog();

            if (addDialog.getValue() == 1) {
                try {
                    activeModel.addRow(p.getInfo());
                } catch (IllegalArgumentException ex) {
                    if (ex.getMessage() == null || ex.getMessage().isEmpty()) {
                        throw ex;
                    } else {
                        JOptionPane.showMessageDialog(MainApplication.getMainFrame(), ex.getMessage(), tr("Error"),
                                JOptionPane.ERROR_MESSAGE);
                    }
                }
            }
        }
    }

    private class RemoveEntryAction extends AbstractAction implements ListSelectionListener {
        private static final long serialVersionUID = 2666450386256004180L;

        /**
         * Constructs a new {@code RemoveEntryAction}.
         */
        RemoveEntryAction() {
            putValue(NAME, tr("Remove"));
            putValue(SHORT_DESCRIPTION, tr("Remove entry"));
            new ImageProvider("dialogs", "delete").getResource().attachImageIcon(this, true);
            updateEnabledState();
        }

        protected final void updateEnabledState() {
            setEnabled(activeTable.getSelectedRowCount() > 0);
        }

        @Override
        public void valueChanged(ListSelectionEvent e) {
            updateEnabledState();
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            Integer i;
            while ((i = activeTable.getSelectedRow()) != -1) {
                activeModel.removeRow(i);
            }
        }
    }

    private class ActivateAction extends AbstractAction implements ListSelectionListener {
        private static final long serialVersionUID = -452335751201424801L;

        /**
         * Constructs a new {@code ActivateAction}.
         */
        ActivateAction() {
            putValue(NAME, tr("Activate"));
            putValue(SHORT_DESCRIPTION, tr("Copy selected default entries from the list above into the list below."));
            new ImageProvider("preferences", "activate-down").getResource().attachImageIcon(this, true);
        }

        protected void updateEnabledState() {
            setEnabled(defaultTable.getSelectedRowCount() > 0);
        }

        @Override
        public void valueChanged(ListSelectionEvent e) {
            updateEnabledState();
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            int[] lines = defaultTable.getSelectedRows();
            if (lines.length == 0) {
                JOptionPane.showMessageDialog(gui, tr("Please select at least one row to copy."), tr("Information"),
                        JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            Set<String> acceptedEulas = new HashSet<>();

            outer: for (int line : lines) {
                MapWithAIInfo info = defaultModel.getRow(defaultTable.convertRowIndexToModel(line));

                // Check if an entry with exactly the same values already exists
                for (int j = 0; j < activeModel.getRowCount(); j++) {
                    if (info.equalsBaseValues(activeModel.getRow(j))) {
                        // Select the already existing row so the user has
                        // some feedback in case an entry exists
                        activeTable.getSelectionModel().setSelectionInterval(j, j);
                        activeTable.scrollRectToVisible(activeTable.getCellRect(j, 0, true));
                        continue outer;
                    }
                }

                activeModel.addRow(new MapWithAIInfo(info));
                int lastLine = activeModel.getRowCount() - 1;
                activeTable.getSelectionModel().setSelectionInterval(lastLine, lastLine);
                activeTable.scrollRectToVisible(activeTable.getCellRect(lastLine, 0, true));
            }
        }
    }

    private class ReloadAction extends AbstractAction {
        private static final long serialVersionUID = 7801339998423585685L;

        /**
         * Constructs a new {@code ReloadAction}.
         */
        ReloadAction() {
            putValue(SHORT_DESCRIPTION, tr("Update default entries"));
            new ImageProvider("dialogs", "refresh").getResource().attachImageIcon(this, true);
        }

        @Override
        public void actionPerformed(ActionEvent evt) {
            layerInfo.loadDefaults(true, MainApplication.worker, false, () -> {
                defaultModel.fireTableDataChanged();
                defaultTable.getSelectionModel().clearSelection();
                defaultTableListener.clearMap();
                /* loading new file may change active layers */
                activeModel.fireTableDataChanged();
            });
        }
    }

    /**
     * The table model for source layer list
     */
    public class MapWithAILayerTableModel extends DefaultTableModel {
        private static final long serialVersionUID = 60378230494588007L;

        /**
         * Constructs a new {@code MapWithAILayerTableModel}.
         */
        public MapWithAILayerTableModel() {
            setColumnIdentifiers(new String[] { tr("Default Source Value"), tr("MapWithAI URL") });
        }

        /**
         * Returns the source info at the given row number.
         *
         * @param row The row number
         * @return The source info at the given row number
         */
        public MapWithAIInfo getRow(int row) {
            return layerInfo.getLayers().get(row);
        }

        /**
         * Adds a new imagery info as the last row.
         *
         * @param i The imagery info to add
         */
        public void addRow(MapWithAIInfo i) {
            layerInfo.add(i);
            int p = getRowCount() - 1;
            fireTableRowsInserted(p, p);
        }

        @Override
        public void removeRow(int i) {
            layerInfo.remove(getRow(i));
            fireTableRowsDeleted(i, i);
        }

        @Override
        public int getRowCount() {
            return layerInfo.getLayers().size();
        }

        @Override
        public Object getValueAt(int row, int column) {
            MapWithAIInfo info = layerInfo.getLayers().get(row);
            switch (column) {
            case 0:
                return info.getName();
            case 1:
                return info.getUrl();
            default:
                throw new ArrayIndexOutOfBoundsException(Integer.toString(column));
            }
        }

        @Override
        public void setValueAt(Object o, int row, int column) {
            if (layerInfo.getLayers().size() <= row) {
                return;
            }
            MapWithAIInfo info = layerInfo.getLayers().get(row);
            switch (column) {
            case 0:
                info.setName((String) o);
                info.clearId();
                break;
            case 1:
                info.setUrl((String) o);
                info.clearId();
                break;
            default:
                throw new ArrayIndexOutOfBoundsException(Integer.toString(column));
            }
        }
    }

    /**
     * The table model for the default imagery layer list
     */
    public class MapWithAIDefaultLayerTableModel extends DefaultTableModel {
        private static final long serialVersionUID = -2966437364160797385L;

        /**
         * Constructs a new {@code MapWithAIDefaultLayerTableModel}.
         */
        public MapWithAIDefaultLayerTableModel() {
            setColumnIdentifiers(new String[] { "", "", tr("Menu Name (Default)"), tr("MapWithAI URL (Default)") });
        }

        /**
         * Returns the imagery info at the given row number.
         *
         * @param row The row number
         * @return The imagery info at the given row number
         */
        public MapWithAIInfo getRow(int row) {
            return layerInfo.getAllDefaultLayers().get(row);
        }

        @Override
        public int getRowCount() {
            return layerInfo.getAllDefaultLayers().size();
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            switch (columnIndex) {
            case 0:
                return MapWithAICategory.class;
            case 1:
                return String.class;
            case 2:
                return MapWithAIInfo.class;
            case 3:
                return String.class;
            default:
                return super.getColumnClass(columnIndex);
            }
        }

        @Override
        public Object getValueAt(int row, int column) {
            MapWithAIInfo info = layerInfo.getAllDefaultLayers().get(row);
            switch (column) {
            case 0:
                return Optional.ofNullable(info.getCategory()).orElse(MapWithAICategory.OTHER);
            case 1:
                return info.getCountryCode();
            case 2:
                return info;
            case 3:
                return info.getUrl();
            default:
                return null;
            }
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    }

    private static boolean confirmEulaAcceptance(PreferenceTabbedPane gui, String eulaUrl) {
        URL url;
        try {
            url = new URL(eulaUrl.replaceAll("\\{lang\\}", LanguageInfo.getWikiLanguagePrefix()));
            JosmEditorPane htmlPane;
            try {
                htmlPane = new JosmEditorPane(url);
            } catch (IOException e1) {
                Logging.trace(e1);
                // give a second chance with a default Locale 'en'
                try {
                    url = new URL(eulaUrl.replaceAll("\\{lang\\}", ""));
                    htmlPane = new JosmEditorPane(url);
                } catch (IOException e2) {
                    Logging.debug(e2);
                    JOptionPane.showMessageDialog(gui, tr("EULA license URL not available: {0}", eulaUrl));
                    return false;
                }
            }
            Box box = Box.createVerticalBox();
            htmlPane.setEditable(false);
            JScrollPane scrollPane = new JScrollPane(htmlPane);
            scrollPane.setPreferredSize(new Dimension(400, 400));
            box.add(scrollPane);
            int option = JOptionPane.showConfirmDialog(MainApplication.getMainFrame(), box,
                    tr("Please abort if you are not sure"), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (option == JOptionPane.YES_OPTION) {
                return true;
            }
        } catch (MalformedURLException e2) {
            JOptionPane.showMessageDialog(gui, tr("Malformed URL for the EULA licence: {0}", eulaUrl));
        }
        return false;
    }
}