// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.rapid.commands;

import static org.openstreetmap.josm.tools.I18n.tr;
import static org.openstreetmap.josm.tools.I18n.trn;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.plugins.rapid.RapiDPlugin;
import org.openstreetmap.josm.plugins.rapid.backend.RapiDDataUtils;
import org.openstreetmap.josm.tools.Logging;

/**
 * Move primitives between datasets (*not* a copy)
 *
 * @author Taylor Smock
 */
public class MovePrimitiveDataSetCommand extends Command {
    private final DataSet to;
    private final DataSet from;
    private final Collection<OsmPrimitive> primitives;
    private SequenceCommand command;
    List<Command> commands;

    public MovePrimitiveDataSetCommand(DataSet to, DataSet from, Collection<OsmPrimitive> primitives) {
        super(to);
        this.to = to;
        this.from = from;
        this.primitives = primitives;
        command = null;
        commands = new ArrayList<>();
    }

    @Override
    public boolean executeCommand() {
        if (to.equals(from))
            return false;
        command = moveCollection(from, to, primitives);
        command.executeCommand();
        return true;
    }
    /**
     * Move primitives from one dataset to another
     *
     * @param to        The receiving dataset
     * @param from      The sending dataset
     * @param selection The primitives to move
     */
    public SequenceCommand moveCollection(DataSet from, DataSet to, Collection<OsmPrimitive> selection) {
        if (from == null || to.isLocked() || from.isLocked()) {
            Logging.error("{0}: Cannot move primitives from {1} to {2}", RapiDPlugin.NAME, from, to);
            return null;
        }

        Collection<OsmPrimitive> allNeededPrimitives = new ArrayList<>();
        RapiDDataUtils.addPrimitivesToCollection(allNeededPrimitives, selection);

        commands.add(new DeletePrimitivesCommand(from, selection, true));
        AddPrimitivesCommand addPrimitivesCommand = new AddPrimitivesCommand(to, allNeededPrimitives, selection);
        commands.add(addPrimitivesCommand);

        return new SequenceCommand(trn("Move {0} OSM Primitive between data sets",
                "Move {0} OSM Primitives between data sets", selection.size(), selection.size()), commands);
    }

    @Override
    public void undoCommand() {
        command.undoCommand();
    }

    @Override
    public String getDescriptionText() {
        return tr("Move OsmPrimitives between layers");
    }

    @Override
    public void fillModifiedData(Collection<OsmPrimitive> modified, Collection<OsmPrimitive> deleted,
            Collection<OsmPrimitive> added) {
        modified.addAll(primitives);
    }
}