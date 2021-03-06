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
package org.eclipse.ditto.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.eclipse.ditto.client.TestConstants.Policy.POLICY_ID;
import static org.eclipse.ditto.client.TestConstants.Thing.THING_ID;
import static org.eclipse.ditto.base.model.acks.AcknowledgementRequest.parseAcknowledgementRequest;
import static org.eclipse.ditto.base.model.acks.DittoAcknowledgementLabel.LIVE_RESPONSE;

import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

import org.eclipse.ditto.client.internal.AbstractDittoClientTest;
import org.eclipse.ditto.client.internal.bus.Classification;
import org.eclipse.ditto.client.live.commands.FeaturesCommandHandling;
import org.eclipse.ditto.client.live.commands.LiveCommandHandler;
import org.eclipse.ditto.client.live.commands.ThingCommandHandling;
import org.eclipse.ditto.client.live.commands.modify.CreateThingLiveCommand;
import org.eclipse.ditto.client.live.commands.modify.CreateThingLiveCommandAnswerBuilder;
import org.eclipse.ditto.client.live.commands.modify.DeleteFeatureLiveCommandAnswerBuilder;
import org.eclipse.ditto.client.live.events.FeatureEventFactory;
import org.eclipse.ditto.client.live.messages.MessageRegistration;
import org.eclipse.ditto.client.live.messages.MessageSender;
import org.eclipse.ditto.client.live.messages.RepliableMessage;
import org.eclipse.ditto.client.management.AcknowledgementsFailedException;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.exceptions.InvalidRqlExpressionException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.messages.model.MessageDirection;
import org.eclipse.ditto.messages.model.MessagePayloadSizeTooLargeException;
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgement;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgements;
import org.eclipse.ditto.things.model.signals.acks.ThingAcknowledgementFactory;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.messages.model.signals.commands.MessageCommand;
import org.eclipse.ditto.messages.model.signals.commands.MessageCommandResponse;
import org.eclipse.ditto.messages.model.signals.commands.SendClaimMessage;
import org.eclipse.ditto.messages.model.signals.commands.SendClaimMessageResponse;
import org.eclipse.ditto.messages.model.signals.commands.SendFeatureMessage;
import org.eclipse.ditto.messages.model.signals.commands.SendFeatureMessageResponse;
import org.eclipse.ditto.messages.model.signals.commands.SendThingMessage;
import org.eclipse.ditto.messages.model.signals.commands.SendThingMessageResponse;
import org.eclipse.ditto.things.model.signals.commands.ThingErrorResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.CreateThing;
import org.eclipse.ditto.things.model.signals.commands.modify.CreateThingResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteFeature;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteFeatureResponse;
import org.eclipse.ditto.things.model.signals.events.FeatureDeleted;
import org.eclipse.ditto.things.model.signals.events.ThingCreated;
import org.junit.Test;

/**
 * Test live channel interactions not covered by tests conforming to the twin-channel interaction patterns
 * (send-command-get-response, subscribe-for-events), namely:
 * - emit event
 * - send message, receive response
 * - subscribe for message, send message
 * - subscribe for message, send acknowledgement
 * - subscribe for command, send response and/or event
 * - subscribe for command, send acknowledgement
 */
public final class DittoClientLiveTest extends AbstractConsumptionDittoClientTest {

    private static final String FEATURE_ID = "someFeature";
    private static final JsonPointer ATTRIBUTE_KEY_NEW = JsonFactory.newPointer("new");
    private static final String ATTRIBUTE_VALUE = "value";
    private static final Feature FEATURE = ThingsModelFactory.newFeatureBuilder()
            .properties(ThingsModelFactory.newFeaturePropertiesBuilder()
                    .set("propertyPointer", "propertyValue")
                    .build())
            .withId(FEATURE_ID)
            .build();

    private static final Thing THING = ThingsModelFactory.newThingBuilder()
            .setId(THING_ID)
            .setPolicyId(POLICY_ID)
            .setAttribute(ATTRIBUTE_KEY_NEW, JsonFactory.newValue(ATTRIBUTE_VALUE))
            .setFeature(FEATURE)
            .build();

    @Test
    public void sendClaimMessage() {
        testMessageSending(client.live().message().to(THING_ID), claim(), claimResponse());
        testMessageSending(client.live().forId(THING_ID).message().to(), claim(), claimResponse());
    }

    @Test
    public void sendThingMessage() {
        testMessageSending(client.live().message().to(THING_ID), thingMessage(), thingMessageResponse());
        testMessageSending(client.live().forId(THING_ID).message().to(), thingMessage(), thingMessageResponse());
    }

    @Test
    public void sendFeatureMessage() {
        testMessageSending(client.live().message().to(THING_ID).featureId(FEATURE_ID), featureMessage(),
                featureMessageResponse());
        testMessageSending(client.live().forId(THING_ID).forFeature(FEATURE_ID).message().to(), featureMessage(),
                featureMessageResponse());
    }

    @Test
    public void sendMessageAndGetErrorResponse() {
        final CompletableFuture<Void> future = new CompletableFuture<>();
        client.live().message().to(THING_ID).subject("request").payload("payload").send((response, error) -> {
            try {
                assertThat(response).describedAs("response").isNull();
                assertThat(error).describedAs("error")
                        .isInstanceOf(MessagePayloadSizeTooLargeException.class);
                future.complete(null);
            } catch (final Throwable e) {
                future.completeExceptionally(e);
            }
        });

        final MessagePayloadSizeTooLargeException error =
                MessagePayloadSizeTooLargeException.newBuilder(9001, 9000)
                        .build();

        final MessageCommand<?, ?> messageCommand = expectMsgClass(SendThingMessage.class);
        final String correlationId = messageCommand.getDittoHeaders().getCorrelationId().orElse(null);
        reply(ThingErrorResponse.of(THING_ID, error, DittoHeaders.newBuilder().correlationId(correlationId).build()));
        try {
            future.get(TIMEOUT, TIME_UNIT);
        } catch (final Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test
    public void sendThingMessageAndGetSuccessAcknowledgements() {
        final CompletableFuture<Void> future = new CompletableFuture<>();
        client.live().message().from(THING_ID)
                .subject("request")
                .headers(DittoHeaders.newBuilder()
                        .putHeader("my-header", "my-header-value")
                        .acknowledgementRequest(
                                parseAcknowledgementRequest("live-response"),
                                parseAcknowledgementRequest("custom")
                        )
                        .build())
                .payload("payload")
                .contentType("text/plain")
                .send(String.class, (message, error) -> {
                    try {
                        if (error != null) {
                            future.completeExceptionally(error);
                        } else {
                            assertThat(message.getHttpStatus()).contains(HttpStatus.ALREADY_REPORTED);
                            assertThat(message.getHeaders())
                                    .contains(new AbstractMap.SimpleEntry<>("my-header", "my-header-value"));
                            assertThat(message.getHeaders().getDirection()).isEqualTo(MessageDirection.FROM);
                            assertThat(message.getSubject()).isEqualTo("request");
                            assertThat(message.getPayload()).contains("payload");
                            future.complete(null);
                        }
                    } catch (final Throwable e) {
                        future.completeExceptionally(e);
                    }
                });

        final SendThingMessage<?> command = expectMsgClass(SendThingMessage.class);
        final SendThingMessageResponse<?> response =
                SendThingMessageResponse.of(command.getEntityId(), command.getMessage(),
                        HttpStatus.ALREADY_REPORTED, command.getDittoHeaders());
        final Acknowledgement liveResponseAck = toLiveResponseAcknowledgement(response);
        final Acknowledgement customAck = Acknowledgement.of(
                AcknowledgementLabel.of("custom"),
                command.getEntityId(),
                HttpStatus.NO_CONTENT,
                command.getDittoHeaders()
        );
        reply(Acknowledgements.of(Arrays.asList(liveResponseAck, customAck), command.getDittoHeaders()));
        try {
            future.get(TIMEOUT, TIME_UNIT);
        } catch (final Exception e) {
            throw new AssertionError(e);
        }
    }

    private static Acknowledgement toLiveResponseAcknowledgement(final SendThingMessageResponse<?> commandResponse) {

        final DittoHeaders liveResponseAckHeaders = commandResponse.getDittoHeaders().toBuilder()
                    .putHeaders(((MessageCommandResponse<?, ?>) commandResponse).getMessage().getHeaders())
                    .build();

        final Optional<JsonValue> payload =
                commandResponse.toJson().getValue(MessageCommandResponse.JsonFields.JSON_MESSAGE.getPointer()
                        .append(MessageCommandResponse.JsonFields.JSON_MESSAGE_PAYLOAD.getPointer()));

        return ThingAcknowledgementFactory.newAcknowledgement(
                LIVE_RESPONSE,
                commandResponse.getEntityId(),
                commandResponse.getHttpStatus(),
                liveResponseAckHeaders,
                payload.orElse(null));
    }

    @Test
    public void sendThingMessageAndGetFailedAcknowledgements() {
        final CompletableFuture<Void> future = new CompletableFuture<>();
        client.live().message().from(THING_ID)
                .subject("request")
                .headers(DittoHeaders.newBuilder()
                        .putHeader("my-header", "my-header-value")
                        .acknowledgementRequest(
                                parseAcknowledgementRequest("live-response"),
                                parseAcknowledgementRequest("custom")
                        )
                        .build())
                .payload("payload")
                .contentType("text/plain")
                .send(String.class, (message, error) -> {
                    try {
                        assertThat(error).isInstanceOf(AcknowledgementsFailedException.class);
                        final Acknowledgements acks = ((AcknowledgementsFailedException) error).getAcknowledgements();
                        assertThat(acks.getHttpStatus()).isEqualTo(HttpStatus.FAILED_DEPENDENCY);
                        assertThat(acks.getAcknowledgement(LIVE_RESPONSE).map(Acknowledgement::getHttpStatus))
                                .contains(HttpStatus.OK);
                        assertThat(acks.getAcknowledgement(AcknowledgementLabel.of("custom"))
                                .map(Acknowledgement::getHttpStatus))
                                .contains(HttpStatus.IM_A_TEAPOT);
                        future.complete(null);
                    } catch (final Throwable e) {
                        future.completeExceptionally(e);
                    }
                });

        final SendThingMessage<?> command = expectMsgClass(SendThingMessage.class);
        final SendThingMessageResponse<?> response =
                SendThingMessageResponse.of(command.getEntityId(), command.getMessage(), HttpStatus.OK,
                        command.getDittoHeaders());
        final Acknowledgement liveResponseAck = Acknowledgement.of(
                LIVE_RESPONSE,
                response.getEntityId(),
                response.getHttpStatus(),
                response.getDittoHeaders(),
                response.toJson().getValueOrThrow(MessageCommand.JsonFields.JSON_MESSAGE)
        );
        final Acknowledgement customAck = Acknowledgement.of(
                AcknowledgementLabel.of("custom"),
                command.getEntityId(),
                HttpStatus.IM_A_TEAPOT,
                command.getDittoHeaders()
        );
        reply(Acknowledgements.of(Arrays.asList(liveResponseAck, customAck), command.getDittoHeaders()));
        try {
            future.get(TIMEOUT, TIME_UNIT);
        } catch (final Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test
    public void sendThingMessageWithoutLiveResponseAckRequest() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> client.live().message().from(THING_ID)
                        .subject("request")
                        .headers(DittoHeaders.newBuilder()
                                .putHeader("my-header", "my-header-value")
                                .acknowledgementRequest(parseAcknowledgementRequest("custom"))
                                .build())
                );
    }

    @Test
    public void emitEvent() {
        client.live().emitEvent(factory -> factory.featureDeleted(THING_ID, FEATURE_ID));
        assertFeatureDeletedEmitted();
    }

    @Test
    public void emitThingEvent() {
        client.live().forId(THING_ID).emitEvent(factory -> factory.featureDeleted(FEATURE_ID));
        assertFeatureDeletedEmitted();
    }

    @Test
    public void emitFeatureEvent() {
        client.live().forId(THING_ID).forFeature(FEATURE_ID).emitEvent(FeatureEventFactory::featureDeleted);
        assertFeatureDeletedEmitted();
    }

    @Test
    public void subscribeForClaimMessage() {
        subscribeForMessageAndSendMessage(claim(), claimResponse(),
                (id, sub, clazz, handler) -> client.live().registerForClaimMessage(id, clazz, handler));
    }

    @Test
    public void subscribeForClaimMessageAsThing() {
        subscribeForMessageAndSendMessage(claim(), claimResponse(),
                (id, sub, clazz, handler) -> client.live().forId(THING_ID).registerForClaimMessage(id, clazz, handler));
    }

    @Test
    public void subscribeForThingMessage() {
        subscribeForMessageAndSendMessage(thingMessage(), thingMessageResponse(), client.live()::registerForMessage);
    }

    @Test
    public void subscribeForThingMessageAsThing() {
        subscribeForMessageAndSendMessage(thingMessage(), thingMessageResponse(),
                client.live().forId(THING_ID)::registerForMessage);
    }

    @Test
    public void subscribeForFeatureMessage() {
        subscribeForMessageAndSendMessage(featureMessage(), featureMessageResponse(),
                client.live()::registerForMessage);
    }

    @Test
    public void subscribeForFeatureMessageAsFeature() {
        subscribeForMessageAndSendMessage(featureMessage(), featureMessageResponse(),
                client.live().forId(THING_ID).forFeature(FEATURE_ID)::registerForMessage);
    }

    @Test
    public void subscribeForCreateThing() {
        testHandleCreateThing(client.live());
    }

    @Test
    public void subscribeForCreateThingAsThing() {
        testHandleCreateThing(client.live().forId(THING_ID));
    }

    @Test
    public void subscribeForDeleteFeature() {
        testHandleDeleteFeature(client.live());
    }

    @Test
    public void subscribeForDeleteFeatureAsThing() {
        testHandleDeleteFeature(client.live().forId(THING_ID));
    }

    @Test
    public void subscribeForDeleteFeatureAsFeature() {
        testHandleDeleteFeature(client.live().forId(THING_ID).forFeature(FEATURE_ID));
    }

    @Test
    public void testThingMessageAcknowledgement() {
        testMessageAcknowledgement(client.live(), thingMessage());
    }

    @Test
    public void testFeatureMessageAcknowledgement() {
        testMessageAcknowledgement(client.live().forId(THING_ID).forFeature(FEATURE_ID), featureMessage());
    }

    @Test
    public void testFeatureMessageResponseAndAcknowledgement() {
        assertEventualCompletion(startConsumption());
        final CompletableFuture<Void> messageReplyFuture = new CompletableFuture<>();
        client.live().forId(THING_ID).forFeature(FEATURE_ID)
                .registerForMessage("Ackermann", "request", String.class, msg ->
                        msg.handleAcknowledgementRequests(handles -> {
                            try {
                                handles.forEach(handle -> handle.acknowledge(HttpStatus.tryGetInstance(
                                        Integer.parseInt(handle.getAcknowledgementLabel().toString()))
                                        .orElse(HttpStatus.EXPECTATION_FAILED)));
                                messageReplyFuture.complete(null);
                            } catch (final Throwable error) {
                                messageReplyFuture.completeExceptionally(error);
                            }
                        }));

        final Signal<?> featureMessage = (((Signal<?>) featureMessage()).setDittoHeaders(DittoHeaders.newBuilder()
                .channel(TopicPath.Channel.LIVE.getName())
                .correlationId("correlation-id")
                .acknowledgementRequest(
                        parseAcknowledgementRequest("live-response"),
                        parseAcknowledgementRequest("100"),
                        parseAcknowledgementRequest("301"),
                        parseAcknowledgementRequest("403")
                )
                .build()
        ));
        reply(featureMessage);
        messageReplyFuture.join();

        assertThat(expectMsgClass(Acknowledgement.class).getHttpStatus()).isEqualTo(HttpStatus.CONTINUE);
        assertThat(expectMsgClass(Acknowledgement.class).getHttpStatus()).isEqualTo(HttpStatus.MOVED_PERMANENTLY);
        assertThat(expectMsgClass(Acknowledgement.class).getHttpStatus()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    public void testThingCommandAcknowledgement() {
        startConsumption();
        client.live().register(LiveCommandHandler.withAcks(
                CreateThingLiveCommand.class,
                acknowledgeable -> {
                    acknowledgeable.handleAcknowledgementRequests(
                            handles -> handles.forEach(handle -> handle.acknowledge(
                                    HttpStatus.tryGetInstance(
                                            Integer.parseInt(handle.getAcknowledgementLabel().toString()))
                                            .orElse(HttpStatus.EXPECTATION_FAILED))));
                    return acknowledgeable.answer().withoutResponse().withoutEvent();
                }
        ));
        final String correlationId = UUID.randomUUID().toString();
        reply(setHeaders(CreateThing.of(THING, null, DittoHeaders.newBuilder()
                .channel(TopicPath.Channel.LIVE.getName())
                .acknowledgementRequest(
                        parseAcknowledgementRequest("100"),
                        parseAcknowledgementRequest("301"),
                        parseAcknowledgementRequest("403")
                )
                .build()), correlationId));

        assertThat(expectMsgClass(Acknowledgement.class).getHttpStatus()).isEqualTo(HttpStatus.CONTINUE);
        assertThat(expectMsgClass(Acknowledgement.class).getHttpStatus()).isEqualTo(HttpStatus.MOVED_PERMANENTLY);
        assertThat(expectMsgClass(Acknowledgement.class).getHttpStatus()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Override
    protected CompletionStage<Void> startConsumptionRequest() {
        return client.live().startConsumption();
    }

    @Override
    protected void replyToConsumptionRequest() {
        expectProtocolMsgWithCorrelationId("START-SEND-LIVE-EVENTS");
        reply("START-SEND-LIVE-EVENTS:ACK");

        expectProtocolMsgWithCorrelationId("START-SEND-MESSAGES");
        reply("START-SEND-MESSAGES:ACK");

        expectProtocolMsgWithCorrelationId("START-SEND-LIVE-COMMANDS");
        reply("START-SEND-LIVE-COMMANDS:ACK");
    }

    @Override
    protected void startConsumptionAndExpectError() {
        final CompletionStage<Void> future = client.live().startConsumption();
        final InvalidRqlExpressionException invalidRqlExpressionException =
                InvalidRqlExpressionException.newBuilder().message("Invalid filter.").build();

        // live-events + live-messages + live-commands = 3
        for (int i = 0; i < 3; i++) {
            final String protocolMessage = messaging.expectEmitted();
            final String correlationId = determineCorrelationId(protocolMessage);
            final DittoHeaders dittoHeaders = DittoHeaders.newBuilder().correlationId(correlationId).build();
            reply(ThingErrorResponse.of(invalidRqlExpressionException.setDittoHeaders(dittoHeaders)));
        }

        assertFailedCompletion(future, InvalidRqlExpressionException.class);
    }

    @Override
    protected void startConsumptionSucceeds() {
        final CompletableFuture<Void> future = client.live().startConsumption().toCompletableFuture();

        reply(Classification.StreamingType.LIVE_EVENT.startAck());
        reply(Classification.StreamingType.LIVE_COMMAND.startAck());
        reply(Classification.StreamingType.LIVE_MESSAGE.startAck());

        future.join();
    }

    private void testMessageAcknowledgement(final MessageRegistration registration, final Signal<?> message) {
        assertEventualCompletion(startConsumption());
        final CompletableFuture<Void> messageHandlingFuture = new CompletableFuture<>();
        registration.registerForMessage("Ackermann", "request", String.class, msg -> {
            msg.handleAcknowledgementRequests(handles -> {
                try {
                    handles.forEach(handle -> handle.acknowledge(
                            HttpStatus.tryGetInstance(Integer.parseInt(handle.getAcknowledgementLabel().toString()))
                                    .orElse(HttpStatus.EXPECTATION_FAILED)
                    ));
                    messageHandlingFuture.complete(null);
                } catch (final Throwable error) {
                    messageHandlingFuture.completeExceptionally(error);
                }
            });
        });

        reply(message.setDittoHeaders(DittoHeaders.newBuilder()
                .channel(TopicPath.Channel.LIVE.getName())
                .acknowledgementRequest(
                        parseAcknowledgementRequest("100"),
                        parseAcknowledgementRequest("301"),
                        parseAcknowledgementRequest("403")
                )
                .build()
        ));
        messageHandlingFuture.join();

        assertThat(expectMsgClass(Acknowledgement.class).getHttpStatus()).isEqualTo(HttpStatus.CONTINUE);
        assertThat(expectMsgClass(Acknowledgement.class).getHttpStatus()).isEqualTo(HttpStatus.MOVED_PERMANENTLY);
        assertThat(expectMsgClass(Acknowledgement.class).getHttpStatus()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private void testHandleCreateThing(final ThingCommandHandling thingCommandHandling) {
        startConsumption();
        thingCommandHandling.handleCreateThingCommands(c -> c.answer()
                .withResponse(CreateThingLiveCommandAnswerBuilder.ResponseFactory::created)
                .withEvent(CreateThingLiveCommandAnswerBuilder.EventFactory::created)
        );
        final String correlationId = UUID.randomUUID().toString();
        reply(setHeaders(CreateThing.of(THING, null, DittoHeaders.empty()), correlationId));
        assertHeaders(expectMsgClass(CreateThingResponse.class), correlationId);
        assertHeaders(expectMsgClass(ThingCreated.class), correlationId);
    }

    private void testHandleDeleteFeature(final FeaturesCommandHandling featuresCommandHandling) {
        startConsumption();
        featuresCommandHandling.handleDeleteFeatureCommands(c -> c.answer()
                .withResponse(DeleteFeatureLiveCommandAnswerBuilder.ResponseFactory::deleted)
                .withEvent(DeleteFeatureLiveCommandAnswerBuilder.EventFactory::deleted)
        );
        final String correlationId = UUID.randomUUID().toString();
        reply(setHeaders(DeleteFeature.of(THING_ID, FEATURE_ID, DittoHeaders.empty()), correlationId));
        assertHeaders(expectMsgClass(DeleteFeatureResponse.class), correlationId);
        assertHeaders(expectMsgClass(FeatureDeleted.class), correlationId);
    }

    private static void assertHeaders(final Signal<?> signal, final String correlationId) {
        assertThat(signal.getDittoHeaders().getCorrelationId()).contains(correlationId);
        assertThat(signal.getDittoHeaders().getChannel()).contains(TopicPath.Channel.LIVE.getName());
    }

    private static Signal<?> setHeaders(final Signal<?> signal, final CharSequence correlationId) {
        return signal.setDittoHeaders(signal.getDittoHeaders()
                .toBuilder()
                .correlationId(correlationId)
                .channel(TopicPath.Channel.LIVE.getName())
                .build());
    }

    private void subscribeForMessageAndSendMessage(final MessageCommand<?, ?> incomingCommand,
            final MessageCommandResponse<String, ?> expectedResponse,
            final MessageHandlerRegistration<String, String> registerForMessage) {

        assertEventualCompletion(startConsumption());
        final String subject = incomingCommand.getMessage().getSubject();
        final String correlationId = UUID.randomUUID().toString();
        registerForMessage.accept(correlationId, subject, String.class, msg -> msg.reply()
                .httpStatus(expectedResponse.getHttpStatus())
                .payload(expectedResponse.getMessage().getPayload().orElse(null))
                .send()
        );
        reply(toAdaptableJsonString(incomingCommand.setDittoHeaders(incomingCommand.getDittoHeaders().toBuilder()
                .correlationId(correlationId)
                .build())));
        final MessageCommandResponse<?, ?> response = expectMsgClass(expectedResponse.getClass());
        assertThat(response.getDittoHeaders().getCorrelationId()).contains(correlationId);
        assertThat(response.getHttpStatus()).isEqualTo(expectedResponse.getHttpStatus());
        assertThat(response.getMessage().getPayload()).isEqualTo(expectedResponse.getMessage().getPayload());
    }

    private static SendClaimMessage<String> claim() {
        final String subject = "claim";
        final String payload = "THOU BELONGEST TO ME!";
        return SendClaimMessage.of(THING_ID,
                AbstractDittoClientTest.<String>newMessageBuilder(subject)
                        .payload(payload)
                        .build(),
                DittoHeaders.newBuilder()
                        .randomCorrelationId()
                        .contentType("text/plain")
                        .build());
    }

    private static SendClaimMessageResponse<String> claimResponse() {
        final String subject = "claim";
        final String responsePayload = "THOU WISHEST!";
        final HttpStatus responseStatus = HttpStatus.PAYMENT_REQUIRED;
        return SendClaimMessageResponse.of(THING_ID,
                AbstractDittoClientTest.<String>newMessageBuilder(subject)
                        .payload(responsePayload)
                        .build(),
                responseStatus,
                DittoHeaders.newBuilder()
                        .contentType("text/plain")
                        .build());
    }

    private static SendFeatureMessage<String> featureMessage() {
        final String payload = "MAKE COFFEE!";
        return SendFeatureMessage.of(THING_ID, FEATURE_ID,
                AbstractDittoClientTest.<String>newFeatureMessageBuilder(FEATURE_ID)
                        .payload(payload)
                        .build(),
                DittoHeaders.newBuilder().randomCorrelationId().build()
        );
    }

    private static SendFeatureMessageResponse<String> featureMessageResponse() {
        final String responsePayload = "MAKE IT THYSELFE.";
        final HttpStatus responseStatus = HttpStatus.IM_A_TEAPOT;
        return SendFeatureMessageResponse.of(THING_ID, FEATURE_ID,
                AbstractDittoClientTest.<String>newFeatureMessageBuilder(FEATURE_ID)
                        .payload(responsePayload)
                        .build(),
                responseStatus,
                DittoHeaders.newBuilder().randomCorrelationId().build());
    }

    private static SendThingMessage<String> thingMessage() {
        final String subject = "request";
        final String payload = "MAKE COFFEE!";
        return SendThingMessage.of(THING_ID,
                AbstractDittoClientTest.<String>newMessageBuilder(subject)
                        .payload(payload)
                        .build(),
                DittoHeaders.newBuilder().randomCorrelationId().build()
        );
    }

    private static SendThingMessageResponse<String> thingMessageResponse() {
        final String subject = "request";
        final String responsePayload = "MAKE IT THYSELFE.";
        final HttpStatus responseStatus = HttpStatus.IM_A_TEAPOT;
        return SendThingMessageResponse.of(THING_ID,
                AbstractDittoClientTest.<String>newMessageBuilder(subject)
                        .payload(responsePayload)
                        .build(),
                responseStatus,
                DittoHeaders.empty());
    }

    private void assertFeatureDeletedEmitted() {
        final FeatureDeleted featureDeleted = expectMsgClass(FeatureDeleted.class);
        assertThat(featureDeleted.getDittoHeaders().getChannel()).contains(TopicPath.Channel.LIVE.getName());
        assertThat((CharSequence) featureDeleted.getEntityId()).isEqualTo(THING_ID);
    }

    private void testMessageSending(final MessageSender.SetSubject<Object> sender,
            final MessageCommand<String, ?> command,
            final MessageCommandResponse<String, ?> expectedResponse) {

        final String subject = command.getMessage().getSubject();
        final String payload = command.getMessage().getPayload().orElse(null);
        final Class<?> messageCommandClass = command.getClass();

        final CompletableFuture<Void> future = new CompletableFuture<>();
        sender.subject(subject).payload(payload).send((response, error) -> {
            try {
                assertThat(response.getSubject()).isEqualTo(expectedResponse.getMessage().getSubject());
                assertThat(response.getHttpStatus()).contains(expectedResponse.getHttpStatus());
                assertThat(response.getPayload().map(buffer -> new String(buffer.array())))
                        .isEqualTo(expectedResponse.getMessage().getPayload());
                future.complete(null);
            } catch (final Throwable e) {
                future.completeExceptionally(e);
            }
        });

        final MessageCommand<?, ?> messageCommand = (MessageCommand<?, ?>) expectMsgClass(messageCommandClass);
        final String correlationId = messageCommand.getDittoHeaders().getCorrelationId().orElse(null);
        reply(expectedResponse.setDittoHeaders(
                expectedResponse.getDittoHeaders().toBuilder().correlationId(correlationId).build()
        ));
        try {
            future.get(TIMEOUT, TIME_UNIT);
        } catch (final Exception e) {
            throw new AssertionError(e);
        }
    }

    private CompletionStage<Void> startConsumption() {
        final CompletionStage<Void> result = client.live().startConsumption();
        expectProtocolMsgWithCorrelationId("START-SEND-LIVE-EVENTS");
        expectProtocolMsgWithCorrelationId("START-SEND-MESSAGES");
        expectProtocolMsgWithCorrelationId("START-SEND-LIVE-COMMANDS");
        reply("START-SEND-LIVE-EVENTS:ACK");
        reply("START-SEND-MESSAGES:ACK");
        reply("START-SEND-LIVE-COMMANDS:ACK");
        return result;
    }

    @FunctionalInterface
    private interface MessageHandlerRegistration<T, U> {

        void accept(String id, String subject, Class<T> clazz, Consumer<RepliableMessage<T, U>> handler);

    }

}
