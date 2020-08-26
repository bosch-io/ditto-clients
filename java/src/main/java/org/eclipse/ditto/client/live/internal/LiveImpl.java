/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.client.live.internal;

import static org.eclipse.ditto.model.base.common.ConditionChecker.argumentNotNull;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.annotation.ParametersAreNonnullByDefault;

import org.eclipse.ditto.client.internal.CommonManagementImpl;
import org.eclipse.ditto.client.internal.HandlerRegistry;
import org.eclipse.ditto.client.internal.OutgoingMessageFactory;
import org.eclipse.ditto.client.internal.bus.AdaptableBus;
import org.eclipse.ditto.client.internal.bus.Classification;
import org.eclipse.ditto.client.internal.bus.JsonPointerSelector;
import org.eclipse.ditto.client.internal.bus.PointerBus;
import org.eclipse.ditto.client.internal.bus.SelectorUtil;
import org.eclipse.ditto.client.live.Live;
import org.eclipse.ditto.client.live.LiveCommandProcessor;
import org.eclipse.ditto.client.live.LiveFeatureHandle;
import org.eclipse.ditto.client.live.LiveThingHandle;
import org.eclipse.ditto.client.live.commands.LiveCommandHandler;
import org.eclipse.ditto.client.live.events.GlobalEventFactory;
import org.eclipse.ditto.client.live.events.internal.ImmutableGlobalEventFactory;
import org.eclipse.ditto.client.live.messages.MessageSerializerRegistry;
import org.eclipse.ditto.client.live.messages.PendingMessage;
import org.eclipse.ditto.client.live.messages.RepliableMessage;
import org.eclipse.ditto.client.messaging.MessagingProvider;
import org.eclipse.ditto.model.base.acks.AcknowledgementLabel;
import org.eclipse.ditto.model.base.acks.DittoAcknowledgementLabel;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.messages.KnownMessageSubjects;
import org.eclipse.ditto.model.messages.Message;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.ProtocolFactory;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.base.WithFeatureId;
import org.eclipse.ditto.signals.commands.live.LiveCommandFactory;
import org.eclipse.ditto.signals.commands.live.base.LiveCommand;
import org.eclipse.ditto.signals.commands.messages.MessageCommand;
import org.eclipse.ditto.signals.commands.messages.MessageCommandResponse;
import org.eclipse.ditto.signals.commands.things.ThingCommand;
import org.eclipse.ditto.signals.events.base.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation for {@link Live}.
 *
 * @since 1.0.0
 */
@ParametersAreNonnullByDefault
public final class LiveImpl extends CommonManagementImpl<LiveThingHandle, LiveFeatureHandle> implements Live {

    private static final Logger LOGGER = LoggerFactory.getLogger(LiveImpl.class);

    private final JsonSchemaVersion schemaVersion;
    private final MessageSerializerRegistry messageSerializerRegistry;
    private final Map<Class<? extends LiveCommand<?, ?>>, LiveCommandHandler<?, ?>> liveCommandHandlers;
    private final Map<Classification.StreamingType, AdaptableBus.SubscriptionId> subscriptionIds;

    private LiveImpl(final MessagingProvider messagingProvider,
            final OutgoingMessageFactory outgoingMessageFactory,
            final PointerBus bus,
            final JsonSchemaVersion schemaVersion,
            final MessageSerializerRegistry messageSerializerRegistry) {
        super(TopicPath.Channel.LIVE,
                messagingProvider,
                outgoingMessageFactory,
                new HandlerRegistry<>(bus),
                bus);

        this.schemaVersion = schemaVersion;
        this.messageSerializerRegistry = messageSerializerRegistry;
        liveCommandHandlers = new ConcurrentHashMap<>();
        subscriptionIds = new ConcurrentHashMap<>();
    }

    /**
     * Creates a new {@code LiveImpl} instance.
     *
     * @param messagingProvider implementation of underlying messaging provider.
     * @param outgoingMessageFactory a factory for messages.
     * @param bus the bus for message routing.
     * @param schemaVersion the json schema version of the messaging protocol.
     * @param messageSerializerRegistry the registry to serialize and de-serialize messages.
     * @return the new {@code LiveImpl} instance.
     */
    public static LiveImpl newInstance(final MessagingProvider messagingProvider,
            final OutgoingMessageFactory outgoingMessageFactory,
            final PointerBus bus,
            final JsonSchemaVersion schemaVersion,
            final MessageSerializerRegistry messageSerializerRegistry) {
        return new LiveImpl(messagingProvider, outgoingMessageFactory, bus, schemaVersion,
                messageSerializerRegistry);
    }

    @Override
    protected LiveThingHandleImpl createThingHandle(final ThingId thingId) {
        return new LiveThingHandleImpl(thingId, getMessagingProvider(),
                getOutgoingMessageFactory(), getHandlerRegistry(), messageSerializerRegistry);
    }

    @Override
    protected LiveFeatureHandleImpl createFeatureHandle(final ThingId thingId, final String featureId) {
        return new LiveFeatureHandleImpl(thingId, featureId, getMessagingProvider(),
                getOutgoingMessageFactory(), getHandlerRegistry(), messageSerializerRegistry);
    }

    @Override
    protected CompletableFuture<Void> doStartConsumption(final Map<String, String> consumptionConfig) {
        final CompletableFuture<Void> completableFutureEvents = new CompletableFuture<>();
        final CompletableFuture<Void> completableFutureMessages = new CompletableFuture<>();
        final CompletableFuture<Void> completableFutureLiveCommands = new CompletableFuture<>();
        final CompletableFuture<Void> completableFutureCombined =
                CompletableFuture.allOf(completableFutureEvents, completableFutureMessages,
                        completableFutureLiveCommands);

        // register message handler which handles live events:
        subscriptionIds.compute(Classification.StreamingType.LIVE_EVENT, (streamingType, previousSubscriptionId) -> {
            final String subscriptionMessage = buildProtocolCommand(streamingType.start(), consumptionConfig);
            messagingProvider.registerSubscriptionMessage(streamingType, subscriptionMessage);
            return subscribe(previousSubscriptionId,
                    streamingType,
                    subscriptionMessage,
                    streamingType.startAck(),
                    completableFutureEvents,
                    CommonManagementImpl::asThingMessage
            );
        });

        // register message handler which handles incoming messages:
        subscriptionIds.compute(Classification.StreamingType.LIVE_MESSAGE, (streamingType, previousSubscriptionId) -> {
            final String subscriptionMessage = buildProtocolCommand(streamingType.start(), consumptionConfig);
            messagingProvider.registerSubscriptionMessage(streamingType, subscriptionMessage);
            return subscribeAndPublishMessage(previousSubscriptionId,
                    streamingType,
                    subscriptionMessage,
                    streamingType.startAck(),
                    completableFutureMessages,
                    adaptable -> bus -> bus.notify(getPointerBusKey(adaptable), adaptableAsLiveMessage(adaptable)));
        });

        // register message handler which handles live commands:
        subscriptionIds.compute(Classification.StreamingType.LIVE_COMMAND, (streamingType, previousSubscriptionId) -> {
            final String subscriptionMessage = buildProtocolCommand(streamingType.start(), consumptionConfig);
            messagingProvider.registerSubscriptionMessage(streamingType, subscriptionMessage);
            return subscribeAndPublishMessage(previousSubscriptionId,
                    streamingType,
                    subscriptionMessage,
                    streamingType.startAck(),
                    completableFutureLiveCommands,
                    adaptable -> bus -> bus.getExecutor().submit(() -> handleLiveCommandOrResponse(adaptable))
            );
        });
        return completableFutureCombined;
    }

    @Override
    public CompletableFuture<Void> suspendConsumption() {
        return CompletableFuture.allOf(
                subscriptionIds.entrySet()
                        .stream()
                        .map(entry -> {
                            final CompletableFuture<Void> future = new CompletableFuture<>();
                            messagingProvider.unregisterSubscriptionMessage(entry.getKey());
                            unsubscribe(entry.getValue(), entry.getKey().stop(), entry.getKey().stopAck(), future);
                            return future;
                        })
                        .toArray(CompletableFuture[]::new)
        );
    }

    private static String getPointerBusKey(final Adaptable adaptable) {
        final TopicPath topic = adaptable.getTopicPath();
        return String.format("/things/%s:%s%s", topic.getNamespace(), topic.getId(), adaptable.getPayload().getPath());
    }

    /*
     * ###### Section
     * ###### "live" Messages
     */

    @Override
    public <T> PendingMessage<T> message() {
        return PendingMessageImpl.of(LOGGER, outgoingMessageFactory, messageSerializerRegistry, PROTOCOL_ADAPTER,
                messagingProvider);
    }

    @Override
    public <T, U> void registerForMessage(final String registrationId,
            final String subject,
            final Class<T> type,
            final Consumer<RepliableMessage<T, U>> handler) {

        checkRegistrationId(registrationId);
        checkSubject(subject);
        argumentNotNull(type, "type");
        checkHandler(handler);

        LiveMessagesUtil.checkSerializerExistForMessageType(messageSerializerRegistry, type, subject);

        // selector for thing messages:
        final JsonPointerSelector thingSelector = "*".equals(subject)
                ? SelectorUtil.formatJsonPointer(LOGGER, "/things/'{thingId}'/'{direction}'/messages/'{subject}'")
                : SelectorUtil.formatJsonPointer(LOGGER, "/things/'{thingId}'/'{direction}'/messages/{0}", subject);
        // selector for feature messages:
        final JsonPointerSelector featureSelector = "*".equals(subject)
                ? SelectorUtil.formatJsonPointer(LOGGER,
                "/things/'{thingId}'/features/'{featureId}'/'{direction}'/messages/'{subject}'")
                : SelectorUtil.formatJsonPointer(LOGGER,
                "/things/'{thingId}'/features/'{featureId}'/'{direction}'/messages/{0}",
                subject);

        getHandlerRegistry().register(registrationId, SelectorUtil.or(thingSelector, featureSelector),
                LiveMessagesUtil.createEventConsumerForRepliableMessage(PROTOCOL_ADAPTER, getMessagingProvider(),
                        getOutgoingMessageFactory(), messageSerializerRegistry, type, handler));
    }

    private static void checkRegistrationId(final String registrationId) {
        argumentNotNull(registrationId, "registrationId");
    }

    private static void checkSubject(final String subject) {
        argumentNotNull(subject, "subject");
    }

    private static void checkHandler(final Consumer<?> handler) {
        argumentNotNull(handler, "handler");
    }

    @Override
    public <U> void registerForMessage(final String registrationId, final String subject,
            final Consumer<RepliableMessage<?, U>> handler) {

        checkRegistrationId(registrationId);
        checkSubject(subject);
        checkHandler(handler);

        // selector for thing messages:
        final JsonPointerSelector thingSelector = "*".equals(subject)
                ? SelectorUtil.formatJsonPointer(LOGGER, "/things/'{thingId}'/'{direction}'/messages/'{subject}'")
                : SelectorUtil.formatJsonPointer(LOGGER, "/things/'{thingId}'/'{direction}'/messages/{0}", subject);
        // selector for feature messages:
        final JsonPointerSelector featureSelector = "*".equals(subject)
                ? SelectorUtil.formatJsonPointer(LOGGER,
                "/things/'{thingId}'/features/'{featureId}'/'{direction}'/messages/'{subject}'")
                : SelectorUtil.formatJsonPointer(LOGGER,
                "/things/'{thingId}'/features/'{featureId}'/'{direction}'/messages/{0}",
                subject);

        getHandlerRegistry().register(registrationId, SelectorUtil.or(thingSelector, featureSelector),
                LiveMessagesUtil.createEventConsumerForRepliableMessage(PROTOCOL_ADAPTER, getMessagingProvider(),
                        getOutgoingMessageFactory(), messageSerializerRegistry, handler));
    }

    @Override
    public <T, U> void registerForClaimMessage(final String registrationId, final Class<T> type,
            final Consumer<RepliableMessage<T, U>> handler) {

        checkRegistrationId(registrationId);
        argumentNotNull(type, "type");
        checkHandler(handler);

        LiveMessagesUtil.checkSerializerExistForMessageType(messageSerializerRegistry, type);

        final JsonPointerSelector selector =
                SelectorUtil.formatJsonPointer(LOGGER, "/things/'{thingId}'/'{direction}'/messages/{0}",
                        KnownMessageSubjects.CLAIM_SUBJECT);

        getHandlerRegistry().register(registrationId, selector,
                LiveMessagesUtil.createEventConsumerForRepliableMessage(PROTOCOL_ADAPTER, getMessagingProvider(),
                        getOutgoingMessageFactory(), messageSerializerRegistry,
                        type, handler));
    }

    @Override
    public <U> void registerForClaimMessage(final String registrationId,
            final Consumer<RepliableMessage<?, U>> handler) {

        checkRegistrationId(registrationId);
        checkHandler(handler);

        final JsonPointerSelector selector =
                SelectorUtil.formatJsonPointer(LOGGER, "/things/'{thingId}'/inbox/messages/{0}",
                        KnownMessageSubjects.CLAIM_SUBJECT);

        getHandlerRegistry().register(registrationId, selector,
                LiveMessagesUtil.createEventConsumerForRepliableMessage(PROTOCOL_ADAPTER, getMessagingProvider(),
                        getOutgoingMessageFactory(), messageSerializerRegistry, handler));
    }

    /*
     * ###### Section
     * ###### Emit "live" Events
     */

    @Override
    public void emitEvent(final Function<GlobalEventFactory, Event<?>> eventFunction) {
        argumentNotNull(eventFunction);
        final GlobalEventFactory globalEventFactory = ImmutableGlobalEventFactory.getInstance(schemaVersion);
        final Event<?> eventToEmit = eventFunction.apply(globalEventFactory);
        getMessagingProvider().emit(signalToJsonString(adjustHeadersForLiveSignal(eventToEmit)));
    }

    @Override
    public Map<Class<? extends LiveCommand<?, ?>>, LiveCommandHandler<?, ?>> getLiveCommandHandlers() {
        return liveCommandHandlers;
    }

    @Override
    public void publishLiveSignal(final Signal<?> signal) {
        getMessagingProvider().emitAdaptable(adaptOutgoingLiveSignal(signal));
    }

    @Override
    public Logger getLogger() {
        return LOGGER;
    }

    private void handleLiveCommandOrResponse(final Adaptable adaptable) {
        if (adaptable.getPayload().getStatus().isPresent()) {
            // is live command response; just publish.
            messagingProvider.getAdaptableBus()
                    .publish(ProtocolFactory.wrapAsJsonifiableAdaptable(adaptable).toJsonString());
        } else {
            // throw ClassCastException when called on signal of incorrect type
            final ThingCommand<?> command = (ThingCommand<?>) PROTOCOL_ADAPTER.fromAdaptable(adaptable);
            handleLiveCommand(LiveCommandFactory.getInstance().getLiveCommand(command));
        }
    }

    private void handleLiveCommand(final LiveCommand<?, ?> liveCommand) {
        boolean handled = false;

        final ThingId thingId = liveCommand.getThingEntityId();
        if (liveCommand instanceof WithFeatureId) {
            final String featureId = ((WithFeatureId) liveCommand).getFeatureId();
            handled = getFeatureHandle(thingId, featureId)
                    .filter(h -> h instanceof LiveCommandProcessor)
                    .map(h -> (LiveCommandProcessor) h)
                    .map(h -> h.processLiveCommand(liveCommand))
                    .orElse(false);
            LOGGER.debug("Live command of type '{}' handled with specific feature handle: {}",
                    liveCommand.getType(), handled);
        }

        if (!handled) {
            handled = getThingHandle(thingId)
                    .filter(h -> h instanceof LiveCommandProcessor)
                    .map(h -> (LiveCommandProcessor) h)
                    .map(h -> h.processLiveCommand(liveCommand))
                    .orElse(false);
            LOGGER.debug("Live command of type '{}' handled with specific thing handle: {}",
                    liveCommand.getType(), handled);
        }

        if (!handled) {
            handled = processLiveCommand(liveCommand);
            LOGGER.debug("Live command of type '{}' handled with global handle: {}",
                    liveCommand.getType(),
                    handled);
        }

        if (!handled) {
            LOGGER.warn("Incoming live command of type '{}'  was not processed.",
                    liveCommand.getType());
        }

    }

    private static Message<?> adaptableAsLiveMessage(final Adaptable adaptable) {
        final Signal<?> signal = PROTOCOL_ADAPTER.fromAdaptable(adaptable);
        if (signal instanceof MessageCommand) {
            return ((MessageCommand<?, ?>) signal).getMessage();
        } else {
            // ClassCastException on incorrect type
            return ((MessageCommandResponse<?, ?>) signal).getMessage();
        }
    }

    @Override
    protected AcknowledgementLabel getThingResponseAcknowledgementLabel() {
        return DittoAcknowledgementLabel.LIVE_RESPONSE;
    }
}
