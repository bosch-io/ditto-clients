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
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteFeatureProperty;

/**
 * An immutable implementation of {@link DeleteFeaturePropertyLiveCommand}.
 *
 * @since 2.0.0
 */
@Immutable
final class DeleteFeaturePropertyLiveCommandImpl
        extends
        AbstractModifyLiveCommand<DeleteFeaturePropertyLiveCommand, DeleteFeaturePropertyLiveCommandAnswerBuilder>
        implements DeleteFeaturePropertyLiveCommand {

    private final String featureId;
    private final JsonPointer propertyPointer;

    /**
     * @throws NullPointerException if {@code command} is {@code null}.
     */
    private DeleteFeaturePropertyLiveCommandImpl(final DeleteFeatureProperty command) {
        super(command);
        featureId = command.getFeatureId();
        propertyPointer = command.getPropertyPointer();
    }

    /**
     * Returns a new instance of {@code DeleteFeaturePropertyLiveCommandImpl}.
     *
     * @param command the command to base the result on.
     * @return the instance.
     * @throws NullPointerException if {@code command} is {@code null}.
     * @throws ClassCastException if {@code command} is not an instance of {@link DeleteFeatureProperty}.
     */
    @Nonnull
    public static DeleteFeaturePropertyLiveCommandImpl of(final Command<?> command) {
        return new DeleteFeaturePropertyLiveCommandImpl((DeleteFeatureProperty) command);
    }

    @Override
    public String getFeatureId() {
        return featureId;
    }

    @Override
    public JsonPointer getPropertyPointer() {
        return propertyPointer;
    }

    @Override
    public Category getCategory() {
        return Category.DELETE;
    }

    @Override
    public DeleteFeaturePropertyLiveCommand setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new DeleteFeaturePropertyLiveCommandImpl(DeleteFeatureProperty.of(getEntityId(), getFeatureId(),
                getPropertyPointer(), dittoHeaders));
    }

    @Override
    public boolean changesAuthorization() {
        return false;
    }

    @Nonnull
    @Override
    public DeleteFeaturePropertyLiveCommandAnswerBuilder answer() {
        return DeleteFeaturePropertyLiveCommandAnswerBuilderImpl.newInstance(this);
    }

    @Nonnull
    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + super.toString() + "]";
    }

}
