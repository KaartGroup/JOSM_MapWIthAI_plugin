// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.rapid.commands;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.EventQueue;
import java.util.Collection;

import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.plugins.rapid.RapiDPlugin;
import org.openstreetmap.josm.plugins.rapid.backend.RapiDLayer;
import org.openstreetmap.josm.tools.Logging;

public class RapiDAddCommand extends Command implements Runnable {
    DataSet editable;
    DataSet rapid;
    Collection<OsmPrimitive> primitives;
    Command command = null;

    /**
     * Add primitives from RapiD to the OSM data layer
     *
     * @param rapidLayer    The rapid layer
     * @param editableLayer The OSM layer
     * @param selection     The primitives to add from RapiD
     */
    public RapiDAddCommand(RapiDLayer rapidLayer, OsmDataLayer editLayer, Collection<OsmPrimitive> selection) {
        this(rapidLayer.getDataSet(), editLayer.getDataSet(), selection);

    }

    /**
     * Add primitives from RapiD to the OSM data layer
     *
     * @param rapid     The rapid dataset
     * @param editable  The OSM dataset
     * @param selection The primitives to add from RapiD
     */
    public RapiDAddCommand(DataSet rapid, DataSet editable, Collection<OsmPrimitive> selection) {
        super(rapid);
        this.rapid = rapid;
        this.editable = editable;
        this.primitives = selection;
    }

    @Override
    public boolean executeCommand() {
        if (EventQueue.isDispatchThread()) {
            new Thread((Runnable) this::run, getClass().getName()).start();
        } else {
            run();
        }
        return true;
    }

    @Override
    public void run() {
        if (rapid.equals(editable)) {
            Logging.error("{0}: DataSet rapid ({1}) should not be the same as DataSet editable ({2})", RapiDPlugin.NAME,
                    rapid, editable);
            throw new IllegalArgumentException();
        }
        synchronized (this) {
            final boolean locked = rapid.isLocked();
            if (locked) {
                rapid.unlock();
            }
            final Command tCommand = new MovePrimitiveDataSetCommand(editable, rapid, primitives);
            final Command createConnectionsCommand = createConnections(editable, primitives);
            command = new SequenceCommand(getDescriptionText(), tCommand, createConnectionsCommand);
            command.executeCommand();

            if (locked) {
                rapid.lock();
            }
        }
    }

    /**
     * Create connections based off of current RapiD syntax
     *
     * @param dataSet    The {@link DataSet} that should have the primitives we are
     *                   connecting to
     * @param collection The primitives with connection information (currently only
     *                   checks Nodes)
     */
    public static Command createConnections(DataSet dataSet, Collection<OsmPrimitive> collection) {
        return new CreateConnectionsCommand(dataSet, collection);
    }

    @Override
    public void undoCommand() {
        final boolean locked = rapid.isLocked();
        if (locked) {
            rapid.unlock();
        }
        synchronized (this) {
            if (command != null) {
                command.undoCommand();
            }
        }
        if (locked) {
            rapid.lock();
        }
    }

    @Override
    public String getDescriptionText() {
        return tr("Add object from RapiD");
    }

    @Override
    public void fillModifiedData(Collection<OsmPrimitive> modified, Collection<OsmPrimitive> deleted,
            Collection<OsmPrimitive> added) {
        modified.addAll(primitives);
    }
}
