/*
 * Copyright 2019-present HiveMQ GmbH
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
package com.hivemq.edge.modules.adapters.impl;

import com.hivemq.adapter.sdk.api.ProtocolAdapter;
import com.hivemq.adapter.sdk.api.ProtocolAdapterPublishBuilder;
import com.hivemq.adapter.sdk.api.events.EventService;
import com.hivemq.adapter.sdk.api.services.ModuleServices;
import com.hivemq.adapter.sdk.api.services.ProtocolAdapterPublishService;
import com.hivemq.extension.sdk.api.annotations.NotNull;
import com.hivemq.extension.sdk.api.annotations.Nullable;

public class ModuleServicesPerModuleImpl implements ModuleServices {

    private final @NotNull ModuleServicesImpl delegate;
    private final @NotNull ProtocolAdapterPublishServicePerAdapter adapterPublishService;
    private final @NotNull EventService eventService;

    public ModuleServicesPerModuleImpl(
            final @Nullable ProtocolAdapter protocolAdapter,
            final @NotNull ModuleServicesImpl delegate,
            final @NotNull EventService eventService) {
        this.delegate = delegate;
        this.eventService = eventService;
        this.adapterPublishService =
                new ProtocolAdapterPublishServicePerAdapter(delegate.adapterPublishService(), protocolAdapter);
    }

    @Override
    public @NotNull ProtocolAdapterPublishService adapterPublishService() {
        return adapterPublishService;
    }

    @Override
    public @NotNull EventService eventService() {
        return eventService;
    }

    public void setAdapter(final @NotNull ProtocolAdapter protocolAdapter) {
        this.adapterPublishService.setAdapter(protocolAdapter);
    }


    private static class ProtocolAdapterPublishServicePerAdapter implements ProtocolAdapterPublishService {

        private final @NotNull ProtocolAdapterPublishService delegate;

        public ProtocolAdapterPublishServicePerAdapter(
                @NotNull final ProtocolAdapterPublishService delegate, @NotNull final ProtocolAdapter adapter) {
            this.delegate = delegate;
        }

        private @NotNull ProtocolAdapter adapter;

        public void setAdapter(final @NotNull ProtocolAdapter adapter) {
            this.adapter = adapter;
        }

        @Override
        public @NotNull ProtocolAdapterPublishBuilder createPublish() {
            final ProtocolAdapterPublishBuilderImpl builder = (ProtocolAdapterPublishBuilderImpl) delegate.createPublish();
            return builder.withAdapter(adapter);
        }
    }
}

