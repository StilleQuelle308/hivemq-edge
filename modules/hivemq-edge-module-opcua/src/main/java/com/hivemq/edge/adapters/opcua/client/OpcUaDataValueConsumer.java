/*
 * Copyright 2023-present HiveMQ GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hivemq.edge.adapters.opcua.client;

import com.hivemq.adapter.sdk.api.ProtocolAdapterPublishBuilder;
import com.hivemq.adapter.sdk.api.ProtocolPublishResult;
import com.hivemq.adapter.sdk.api.events.EventService;
import com.hivemq.adapter.sdk.api.events.model.Event;
import com.hivemq.adapter.sdk.api.events.model.Payload;
import com.hivemq.adapter.sdk.api.factories.AdapterFactories;
import com.hivemq.adapter.sdk.api.services.ProtocolAdapterMetricsService;
import com.hivemq.adapter.sdk.api.services.ProtocolAdapterPublishService;
import com.hivemq.edge.adapters.opcua.OpcUaAdapterConfig;
import com.hivemq.edge.adapters.opcua.OpcUaProtocolAdapter;
import com.hivemq.edge.adapters.opcua.payload.OpcUaJsonPayloadConverter;
import com.hivemq.edge.adapters.opcua.payload.OpcUaStringPayloadConverter;
import com.hivemq.edge.adapters.opcua.util.Bytes;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class OpcUaDataValueConsumer implements Consumer<DataValue> {
    private static final Logger log = LoggerFactory.getLogger(OpcUaDataValueConsumer.class);

    public static final byte[] EMTPY_BYTES = new byte[]{};

    private final @NotNull OpcUaAdapterConfig.Subscription subscription;
    private final @NotNull ProtocolAdapterPublishService adapterPublishService;
    private final @NotNull OpcUaClient opcUaClient;
    private final @NotNull NodeId nodeId;
    private final @NotNull ProtocolAdapterMetricsService metricsHelper;
    private final @NotNull EventService eventService;
    private final @NotNull OpcUaProtocolAdapter protocolAdapter;
    private final @NotNull AdapterFactories adapterFactories;
    private final @NotNull String adapterId;
    private final @NotNull AtomicBoolean firstMessageReceived = new AtomicBoolean(false);

    public OpcUaDataValueConsumer(
            final @NotNull OpcUaAdapterConfig.Subscription subscription,
            final @NotNull ProtocolAdapterPublishService adapterPublishService,
            final @NotNull OpcUaClient opcUaClient,
            final @NotNull NodeId nodeId,
            final @NotNull ProtocolAdapterMetricsService metricsHelper,
            final @NotNull String adapterId,
            final @NotNull EventService eventService,
            final @NotNull OpcUaProtocolAdapter protocolAdapter,
            final @NotNull AdapterFactories adapterFactories) {
        this.subscription = subscription;
        this.adapterPublishService = adapterPublishService;
        this.opcUaClient = opcUaClient;
        this.nodeId = nodeId;
        this.adapterId = adapterId;
        this.metricsHelper = metricsHelper;
        this.eventService = eventService;
        this.protocolAdapter = protocolAdapter;
        this.adapterFactories = adapterFactories;
    }

    @Override
    public void accept(final @NotNull DataValue dataValue) {
        try {

            final @NotNull byte[] convertedPayload = convertPayload(dataValue, OpcUaAdapterConfig.PayloadMode.JSON);
            final ProtocolAdapterPublishBuilder publishBuilder = adapterPublishService.createPublish()
                    .withTopic(subscription.getMqttTopic())
                    .withPayload(convertedPayload)
                    .withQoS(subscription.getQos())
                    .withContextInformation("opcua-node-id", nodeId.toParseableString());

            if (subscription.getMessageExpiryInterval() != null) {
                publishBuilder.withMessageExpiryInterval(subscription.getMessageExpiryInterval());
            }

            try {

                final EndpointDescription endpoint = opcUaClient.getStackClient().getConfig().getEndpoint();
                if (endpoint != null) {
                    publishBuilder.withContextInformation("opcua-server-endpoint-url", endpoint.getEndpointUrl());
                    publishBuilder.withContextInformation("opcua-server-application-uri",
                            endpoint.getServer().getApplicationUri());
                }
            } catch (Exception e) {
                //ignore, but log
                log.debug("Not able to get dynamic context infos for OPC UA message for adapter {}", adapterId);
            }


            if (firstMessageReceived.compareAndSet(false, true)) {
                eventService.createAdapterEvent(adapterId, protocolAdapter.getId())
                        .withSeverity(Event.SEVERITY.INFO)
                        .withMessage(String.format("Adapter '%s' took first sample to be published to '%s'",
                                adapterId,
                                subscription.getMqttTopic()))
                        .withPayload(Payload.ContentType.JSON, new String(convertedPayload, StandardCharsets.UTF_8))
                        .fire();

            }
            final CompletableFuture<ProtocolPublishResult> publishFuture = publishBuilder.send();

            publishFuture.thenAccept(publishReturnCode -> {
                metricsHelper.incrementReadPublishSuccess();
            }).exceptionally(throwable -> {
                metricsHelper.incrementReadPublishFailure();
                return null;
            });

        } catch (Exception e) {
            log.error("Error on creating MQTT publish from OPC-UA subscription for adapter {}", adapterId, e);
        }
    }

    private @NotNull byte @NotNull [] convertPayload(
            DataValue dataValue, final @NotNull OpcUaAdapterConfig.PayloadMode payloadMode) {
        //null value, emtpy buffer
        if (dataValue.getValue().getValue() == null) {
            return EMTPY_BYTES;
        }

        if (payloadMode == null) {
            return Bytes.fromReadOnlyBuffer(OpcUaJsonPayloadConverter.convertPayload(opcUaClient, dataValue));
        }
        //option to choose different encoding types here -> string vs. json ...
        switch (payloadMode) {
            case STRING:
                return Bytes.fromReadOnlyBuffer(OpcUaStringPayloadConverter.convertPayload(dataValue));
            case JSON:
            default:
                return Bytes.fromReadOnlyBuffer(OpcUaJsonPayloadConverter.convertPayload(opcUaClient, dataValue));
        }
    }
}
