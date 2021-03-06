/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.nativebinaries.toolchain.internal;

import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.internal.DefaultPolymorphicDomainObjectContainer;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.text.TreeFormatter;
import org.gradle.nativebinaries.platform.Platform;
import org.gradle.nativebinaries.toolchain.ToolChain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DefaultToolChainRegistry extends DefaultPolymorphicDomainObjectContainer<ToolChain> implements ToolChainRegistryInternal {
    private final Map<String, Class<? extends ToolChain>> registeredDefaults = new LinkedHashMap<String, Class<? extends ToolChain>>();
    private final List<ToolChainInternal> searchOrder = new ArrayList<ToolChainInternal>();

    public DefaultToolChainRegistry(Instantiator instantiator) {
        super(ToolChain.class, instantiator);
        whenObjectAdded(new Action<ToolChain>() {
            public void execute(ToolChain toolChain) {
                searchOrder.add((ToolChainInternal) toolChain);
            }
        });
        whenObjectRemoved(new Action<ToolChain>() {
            public void execute(ToolChain toolChain) {
                searchOrder.remove(toolChain);
            }
        });
    }

    @Override
    protected void handleAttemptToAddItemWithNonUniqueName(ToolChain toolChain) {
        throw new InvalidUserDataException(String.format("ToolChain with name '%s' added multiple times", toolChain.getName()));
    }

    public void registerDefaultToolChain(String name, Class<? extends ToolChain> type) {
        registeredDefaults.put(name, type);
    }

    public void addDefaultToolChains() {
        for (String name : registeredDefaults.keySet()) {
            create(name, registeredDefaults.get(name));
        }
    }

    public ToolChain getForPlatform(Platform targetPlatform) {
        for (ToolChainInternal toolChain : searchOrder) {
            if (toolChain.getAvailability().isAvailable() && toolChain.canTargetPlatform(targetPlatform)) {
                return toolChain;
            }
        }

        // No tool chains can build for this platform. Assemble a description of why

        TreeFormatter failureMessage = new TreeFormatter();
        failureMessage.node(String.format("No tool chain is available to build for platform '%s'", targetPlatform.getName()));
        failureMessage.startChildren();
        for (ToolChainInternal toolChain : searchOrder) {
            if (!toolChain.getAvailability().isAvailable()) {
                failureMessage.node(toolChain.getDisplayName());
                failureMessage.startChildren();
                toolChain.getAvailability().explain(failureMessage);
                failureMessage.endChildren();
            } else {
                failureMessage.node(String.format("%s cannot build for platform '%s'.", toolChain.getDisplayName(), targetPlatform.getName()));
            }
        }
        if (searchOrder.isEmpty()) {
            failureMessage.node("No tool chain plugin applied.");
        }
        failureMessage.endChildren();

        return new UnavailableToolChain(failureMessage.toString());
    }

    private static class UnavailableToolChain implements ToolChainInternal {
        private final String failureMessage;
        private final OperatingSystem operatingSystem = OperatingSystem.current();

        UnavailableToolChain(String failureMessage) {
            this.failureMessage = failureMessage;
        }

        public String getDisplayName() {
            return getName();
        }

        public String getName() {
            return "unavailable";
        }

        public PlatformToolChain target(Platform targetPlatform) {
            throw failure();
        }

        public boolean canTargetPlatform(Platform targetPlatform) {
            return false;
        }

        private RuntimeException failure() {
            return new GradleException(failureMessage);
        }

        public ToolChainAvailability getAvailability() {
            return new ToolChainAvailability().unavailable("No tool chain is available.");
        }

        public String getExecutableName(String executablePath) {
            return operatingSystem.getExecutableName(executablePath);
        }

        public String getSharedLibraryName(String libraryName) {
            return operatingSystem.getSharedLibraryName(libraryName);
        }

        public String getSharedLibraryLinkFileName(String libraryName) {
            return getSharedLibraryName(libraryName);
        }

        public String getStaticLibraryName(String libraryName) {
            return operatingSystem.getStaticLibraryName(libraryName);
        }

        public String getOutputType() {
            return "unavailable";
        }
    }
}