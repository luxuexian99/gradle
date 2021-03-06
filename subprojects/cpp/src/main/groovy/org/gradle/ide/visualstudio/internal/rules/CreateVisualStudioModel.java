/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.ide.visualstudio.internal.rules;

import org.gradle.ide.visualstudio.VisualStudioExtension;
import org.gradle.ide.visualstudio.internal.DefaultVisualStudioExtension;
import org.gradle.language.base.BinaryContainer;
import org.gradle.model.ModelRule;
import org.gradle.nativebinaries.NativeBinary;
import org.gradle.nativebinaries.NativeComponent;
import org.gradle.nativebinaries.NativeComponentBinary;

@SuppressWarnings("UnusedDeclaration")
public class CreateVisualStudioModel extends ModelRule {
    public void createVisualStudioModelForBinaries(VisualStudioExtension visualStudioExtension, BinaryContainer binaryContainer) {
        DefaultVisualStudioExtension vsExtension = (DefaultVisualStudioExtension) visualStudioExtension;
        for (NativeComponentBinary binary : binaryContainer.withType(NativeComponentBinary.class)) {
            vsExtension.getProjectRegistry().addProjectConfiguration(binary);

            if (isDevelopmentBinary(binary)) {
                vsExtension.getSolutionRegistry().addSolution(binary);
            }
        }
    }

    private boolean isDevelopmentBinary(NativeComponentBinary binary) {
        return binary == chooseDevelopmentVariant(binary.getComponent());
    }

    private NativeBinary chooseDevelopmentVariant(NativeComponent component) {
        for (NativeBinary candidate : component.getBinaries()) {
            if (candidate.isBuildable()) {
                return candidate;
            }
        }
        return null;
    }
}

