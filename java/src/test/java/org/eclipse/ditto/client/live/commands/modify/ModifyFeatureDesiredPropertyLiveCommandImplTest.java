/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.eclipse.ditto.base.model.signals.commands.assertions.CommandAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.text.MessageFormat;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.client.live.TestConstants;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeatureDesiredProperty;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link org.eclipse.ditto.client.live.commands.modify.ModifyFeatureDesiredPropertyLiveCommandImpl}.
 */
public final class ModifyFeatureDesiredPropertyLiveCommandImplTest {

    private ModifyFeatureDesiredProperty twinCommand;
    private ModifyFeatureDesiredPropertyLiveCommand underTest;

    @Before
    public void setUp() {
        twinCommand = ModifyFeatureDesiredProperty.of(TestConstants.Thing.THING_ID, TestConstants.Feature.HOVER_BOARD_ID,
                TestConstants.Feature.HOVER_BOARD_PROPERTY_POINTER,
                TestConstants.Feature.HOVER_BOARD_PROPERTY_VALUE, DittoHeaders.empty());
        underTest = ModifyFeatureDesiredPropertyLiveCommandImpl.of(twinCommand);
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(ModifyFeatureDesiredPropertyLiveCommandImpl.class,
                areImmutable(),
                provided(JsonPointer.class, JsonValue.class).areAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ModifyFeatureDesiredPropertyLiveCommandImpl.class)
                .withRedefinedSuperclass()
                .withIgnoredFields("thingModifyCommand", "featureId", "desiredPropertyPointer", "desiredPropertyValue")
                .verify();
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void tryToGetModifyFeatureDesiredPropertyLiveCommandForNull() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> ModifyFeatureDesiredPropertyLiveCommandImpl.of(null))
                .withMessage(MessageFormat.format("The {0} must not be null!", "command"))
                .withNoCause();
    }

    @Test
    public void tryToGetModifyFeatureDesiredPropertyLiveCommandForCreateThingCommand() {
        final Command<?> commandMock = Mockito.mock(Command.class);

        assertThatExceptionOfType(ClassCastException.class)
                .isThrownBy(() -> ModifyFeatureDesiredPropertyLiveCommandImpl.of(commandMock))
                .withMessageContaining(
                        ModifyFeatureDesiredProperty.class.getName())
                .withNoCause();
    }

    @Test
    public void getModifyFeatureDesiredPropertyLiveCommandReturnsExpected() {
        assertThat(underTest)
                .withType(twinCommand.getType())
                .withDittoHeaders(twinCommand.getDittoHeaders())
                .withId(twinCommand.getEntityId())
                .withManifest(twinCommand.getManifest())
                .withResourcePath(twinCommand.getResourcePath());
        assertThat(underTest.getFeatureId()).isEqualTo(twinCommand.getFeatureId());
        assertThat(underTest.getDesiredPropertyPointer()).isEqualTo(twinCommand.getDesiredPropertyPointer());
        assertThat(underTest.getDesiredPropertyValue()).isEqualTo(twinCommand.getDesiredPropertyValue());
    }

    @Test
    public void setDittoHeadersReturnsExpected() {
        final DittoHeaders emptyDittoHeaders = DittoHeaders.empty();
        final ModifyFeatureDesiredPropertyLiveCommand newModifyFeatureDesiredPropertyLiveCommand =
                underTest.setDittoHeaders(emptyDittoHeaders);

        assertThat(newModifyFeatureDesiredPropertyLiveCommand).withDittoHeaders(emptyDittoHeaders);
    }

    @Test
    public void answerReturnsNotNull() {
        assertThat(underTest.answer()).isNotNull();
    }

    @Test
    public void toStringReturnsExpected() {
        assertThat(underTest.toString())
                .contains(underTest.getClass().getSimpleName())
                .contains("command=")
                .contains(twinCommand.toString());
    }

}
