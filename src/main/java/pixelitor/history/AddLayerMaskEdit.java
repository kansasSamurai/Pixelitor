/*
 * Copyright 2025 Laszlo Balazs-Csiki and Contributors
 *
 * This file is part of Pixelitor. Pixelitor is free software: you
 * can redistribute it and/or modify it under the terms of the GNU
 * General Public License, version 3 as published by the Free
 * Software Foundation.
 *
 * Pixelitor is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Pixelitor. If not, see <http://www.gnu.org/licenses/>.
 */

package pixelitor.history;

import pixelitor.AppMode;
import pixelitor.Composition;
import pixelitor.layers.Layer;
import pixelitor.layers.LayerMask;
import pixelitor.layers.MaskViewMode;
import pixelitor.utils.debug.DebugNode;

import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;

/**
 * A PixelitorEdit that represents the adding of a layer mask.
 */
public class AddLayerMaskEdit extends PixelitorEdit {
    private final Layer layer;
    private final LayerMask layerMask;
    private MaskViewMode newMode;

    public AddLayerMaskEdit(String name, Composition comp, Layer layer) {
        super(name, comp);

        this.layer = layer;
        layerMask = layer.getMask();

        assert layer.isActive() || AppMode.isUnitTesting() : genNotActiveLayerErrorMsg();
    }

    @Override
    public void undo() throws CannotUndoException {
        super.undo();

        // has to be saved here, because when the constructor is
        // called, we don't know yet the mode before the undo
        newMode = comp.getView().getMaskViewMode();

        assert layer.isActive() || AppMode.isUnitTesting() : genNotActiveLayerErrorMsg();

        layer.deleteMask(false);
    }

    @Override
    public void redo() throws CannotRedoException {
        super.redo();

        layer.addConfiguredMask(layerMask);

        assert newMode != null;
        newMode.activate(comp, layer);
    }

    private String genNotActiveLayerErrorMsg() {
        return "%s '%s' in '%s' is not active".formatted(
            layer.getTypeString(), layer.getName(), comp.getName());
    }

    @Override
    public DebugNode createDebugNode(String key) {
        DebugNode node = super.createDebugNode(key);

        node.add(layer.createDebugNode());
        node.add(layerMask.createDebugNode());
        node.addAsString("new mask view node", newMode);

        return node;
    }
}
