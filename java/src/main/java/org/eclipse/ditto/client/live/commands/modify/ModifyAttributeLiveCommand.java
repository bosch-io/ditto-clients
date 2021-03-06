/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.client.live.commands.modify;

import javax.annotation.Nonnull;

import org.eclipse.ditto.client.live.commands.base.LiveCommand;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyAttribute;
import org.eclipse.ditto.things.model.signals.commands.modify.ThingModifyCommand;

/**
 * {@link ModifyAttribute} live command giving access to the command and all of its special accessors. Also the entry
 * point for creating a {@link ModifyAttributeLiveCommandAnswerBuilder} capable of answering incoming commands.
 *
 * @since 2.0.0
 */
public interface ModifyAttributeLiveCommand
        extends LiveCommand<ModifyAttributeLiveCommand, ModifyAttributeLiveCommandAnswerBuilder>,
        ThingModifyCommand<ModifyAttributeLiveCommand> {

    /**
     * Returns the JSON pointer of the attribute to modify.
     *
     * @return the JSON pointer.
     * @see ModifyAttribute#getAttributePointer()
     */
    @Nonnull
    JsonPointer getAttributePointer();

    /**
     * Returns the value of the attribute to modify.
     *
     * @return the value.
     * @see ModifyAttribute#getAttributeValue()
     */
    @Nonnull
    JsonValue getAttributeValue();

}
