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
import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeatureProperty;

/**
 * An immutable implementation of {@link ModifyFeaturePropertyLiveCommand}.
 *
 * @since 2.0.0
 */
@ParametersAreNonnullByDefault
@Immutable
final class ModifyFeaturePropertyLiveCommandImpl
        extends
        AbstractModifyLiveCommand<ModifyFeaturePropertyLiveCommand, ModifyFeaturePropertyLiveCommandAnswerBuilder>
        implements ModifyFeaturePropertyLiveCommand {

    private final String featureId;
    private final JsonPointer propertyPointer;
    private final JsonValue propertyValue;

    private ModifyFeaturePropertyLiveCommandImpl(final ModifyFeatureProperty command) {
        super(command);
        featureId = command.getFeatureId();
        propertyPointer = command.getPropertyPointer();
        propertyValue = command.getPropertyValue();
    }

    /**
     * Returns a new instance of {@code ModifyFeaturePropertyLiveCommandImpl}.
     *
     * @param command the command to base the result on.
     * @return the instance.
     * @throws NullPointerException if {@code command} is {@code null}.
     * @throws ClassCastException if {@code command} is not an instance of {@link ModifyFeatureProperty}.
     */
    @Nonnull
    public static ModifyFeaturePropertyLiveCommandImpl of(final Command<?> command) {
        return new ModifyFeaturePropertyLiveCommandImpl((ModifyFeatureProperty) command);
    }

    @Override
    public String getFeatureId() {
        return featureId;
    }

    @Nonnull
    @Override
    public JsonPointer getPropertyPointer() {
        return propertyPointer;
    }

    @Nonnull
    @Override
    public JsonValue getPropertyValue() {
        return propertyValue;
    }

    @Override
    public Category getCategory() {
        return Category.MODIFY;
    }

    @Override
    public ModifyFeaturePropertyLiveCommand setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new ModifyFeaturePropertyLiveCommandImpl(ModifyFeatureProperty.of(getEntityId(), getFeatureId(),
                getPropertyPointer(), getPropertyValue(), dittoHeaders));
    }

    @Override
    public boolean changesAuthorization() {
        return false;
    }

    @Nonnull
    @Override
    public ModifyFeaturePropertyLiveCommandAnswerBuilder answer() {
        return ModifyFeaturePropertyLiveCommandAnswerBuilderImpl.newInstance(this);
    }

    @Nonnull
    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + super.toString() + "]";
    }

}
