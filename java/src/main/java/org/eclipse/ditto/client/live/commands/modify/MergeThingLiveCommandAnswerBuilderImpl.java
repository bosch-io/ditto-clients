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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.time.Instant;
import java.util.function.Function;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.things.model.signals.commands.ThingErrorResponse;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingNotModifiableException;
import org.eclipse.ditto.things.model.signals.commands.modify.MergeThingResponse;
import org.eclipse.ditto.base.model.signals.events.Event;
import org.eclipse.ditto.things.model.signals.events.ThingMerged;

/**
 * A mutable builder with a fluent API for creating a {@link org.eclipse.ditto.client.live.commands.base.LiveCommandAnswer}
 * for a {@link MergeThingLiveCommand}.
 *
 * @since 2.0.0
 */
@ParametersAreNonnullByDefault
@NotThreadSafe
final class MergeThingLiveCommandAnswerBuilderImpl
        extends
        AbstractLiveCommandAnswerBuilder<MergeThingLiveCommand, MergeThingLiveCommandAnswerBuilder.ResponseFactory, MergeThingLiveCommandAnswerBuilder.EventFactory>
        implements MergeThingLiveCommandAnswerBuilder {

    private MergeThingLiveCommandAnswerBuilderImpl(final MergeThingLiveCommand command) {
        super(command);
    }

    /**
     * Returns a new instance of {@code MergeThingLiveCommandAnswerBuilderImpl}.
     *
     * @param command the command to build an answer for.
     * @return the instance.
     * @throws NullPointerException if {@code command} is {@code null}.
     */
    public static MergeThingLiveCommandAnswerBuilderImpl newInstance(final MergeThingLiveCommand command) {
        checkNotNull(command, "command");
        return new MergeThingLiveCommandAnswerBuilderImpl(command);
    }

    @Override
    protected CommandResponse doCreateResponse(
            final Function<ResponseFactory, CommandResponse<?>> createResponseFunction) {
        return createResponseFunction.apply(new ResponseFactoryImpl());
    }

    @Override
    protected Event doCreateEvent(final Function<EventFactory, Event<?>> createEventFunction) {
        return createEventFunction.apply(new EventFactoryImpl());
    }

    @Immutable
    private final class ResponseFactoryImpl implements ResponseFactory {

        @Nonnull
        @Override
        public MergeThingResponse merged() {
            return MergeThingResponse.of(command.getEntityId(), command.getPath(), command.getDittoHeaders());
        }

        @Nonnull
        @Override
        public ThingErrorResponse thingNotAccessibleError() {
            return errorResponse(command.getEntityId(),
                    ThingNotAccessibleException.newBuilder(command.getEntityId())
                            .dittoHeaders(command.getDittoHeaders())
                            .build());
        }

        @Nonnull
        @Override
        public ThingErrorResponse thingNotModifiableError() {
            return errorResponse(command.getEntityId(),
                    ThingNotModifiableException.newBuilder(command.getEntityId())
                            .dittoHeaders(command.getDittoHeaders())
                            .build());
        }
    }

    @Immutable
    private final class EventFactoryImpl implements EventFactory {

        @Nonnull
        @Override
        public ThingMerged merged() {
            return ThingMerged.of(command.getEntityId(), command.getPath(), command.getValue(), -1, Instant.now(),
                    command.getDittoHeaders(), null);
        }
    }

}
