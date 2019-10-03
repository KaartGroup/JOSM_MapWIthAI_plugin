package org.openstreetmap.josm.plugins.rapid.backend;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.plugins.rapid.RapiDPlugin;
import org.openstreetmap.josm.plugins.rapid.commands.RapiDAddCommand;
import org.openstreetmap.josm.tools.Shortcut;

public class RapiDMoveAction extends JosmAction {
    /** UID for abstract action */
    private static final long serialVersionUID = 319374598;

    public RapiDMoveAction() {
        super(tr("Add from ".concat(RapiDPlugin.NAME)), null, tr("Add data from RapiD"), Shortcut.registerShortcut(
                "data:rapidadd", tr("Rapid: {0}", tr("Add selected data")), KeyEvent.VK_A, Shortcut.SHIFT), true);
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        for (final RapiDLayer rapid : MainApplication.getLayerManager().getLayersOfType(RapiDLayer.class)) {
            final List<OsmDataLayer> osmLayers = MainApplication.getLayerManager().getLayersOfType(OsmDataLayer.class);
            OsmDataLayer editLayer = null;
            final int maxAddition = RapiDDataUtils.getMaximumAddition();
            Collection<OsmPrimitive> selected;
            if (maxAddition > 0) {
                selected = rapid.getDataSet().getSelected().stream().limit(maxAddition).collect(Collectors.toList());
            } else {
                selected = rapid.getDataSet().getSelected();
            }
            for (final OsmDataLayer osmLayer : osmLayers) {
                if (!osmLayer.isLocked() && osmLayer.isVisible() && osmLayer.isUploadable()
                        && osmLayer.getClass().equals(OsmDataLayer.class)) {
                    editLayer = osmLayer;
                    break;
                }
            }
            if (editLayer != null) {
                final RapiDAddCommand command = new RapiDAddCommand(rapid, editLayer, selected);
                UndoRedoHandler.getInstance().add(command);
                if (RapiDDataUtils.getSwitchLayers()) {
                    MainApplication.getLayerManager().setActiveLayer(editLayer);
                    final DataSet editable = editLayer.getDataSet();
                    editable.setSelected(
                            editable.getSelected().stream().filter(OsmPrimitive::isTagged).collect(Collectors.toSet()));
                }
            }
        }
    }

    @Override
    protected void updateEnabledState() {
        setEnabled(checkIfActionEnabled());
    }

    @Override
    protected void updateEnabledState(Collection<? extends OsmPrimitive> selection) {
        if (selection == null || selection.isEmpty()) {
            setEnabled(false);
        } else {
            setEnabled(checkIfActionEnabled());
        }
    }

    private boolean checkIfActionEnabled() {
        boolean returnValue = false;
        final Layer active = getLayerManager().getActiveLayer();
        if (active instanceof RapiDLayer) {
            final RapiDLayer rapid = (RapiDLayer) active;
            final Collection<OsmPrimitive> selection = rapid.getDataSet().getAllSelected();
            returnValue = selection != null && !selection.isEmpty();
        }
        return returnValue;
    }
}
