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

package org.gradle.nativebinaries.language.cpp

import org.gradle.internal.os.OperatingSystem
import org.gradle.nativebinaries.language.cpp.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativebinaries.language.cpp.fixtures.app.PlatformDetectingTestApp
import org.gradle.nativebinaries.language.cpp.fixtures.binaryinfo.DumpbinBinaryInfo
import org.gradle.nativebinaries.language.cpp.fixtures.binaryinfo.OtoolBinaryInfo
import org.gradle.nativebinaries.language.cpp.fixtures.binaryinfo.ReadelfBinaryInfo
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

@Requires(TestPrecondition.NOT_UNKNOWN_OS)
class BinaryPlatformIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    def testApp = new PlatformDetectingTestApp()
    def os = OperatingSystem.current()

    def setup() {
        buildFile << """
            apply plugin: 'cpp'

            executables {
                main {}
            }
        """

        testApp.writeSources(file("src/main"))
    }

    def "configure component for a single target platform"() {
        when:
        buildFile << """
            model {
                platforms {
                    create("x86") {
                        architecture "x86"
                    }
                    create("x86_64") {
                        architecture "x86_64"
                    }
                }
            }
            task buildExecutables {
                dependsOn binaries.withType(ExecutableBinary).matching {
                    it.buildable
                }
            }
            executables.main.targetPlatforms "x86"
"""

        and:
        succeeds "buildExecutables"

        then:
        // Platform dimension is flattened since there is only one possible value
        executedAndNotSkipped(":mainExecutable")
        executable("build/binaries/mainExecutable/main").binaryInfo.arch.name == "x86"
        executable("build/binaries/mainExecutable/main").exec().out == "i386 ${os.familyName}"
    }

    def "build binary for multiple target architectures"() {
        when:
        buildFile << """
            model {
                platforms {
                    create("x86") {
                        architecture "x86"
                    }
                    create("x86_64") {
                        architecture "x86_64"
                    }
                    create("itanium") {
                        architecture "ia-64"
                    }
                    create("arm") {
                        architecture "arm"
                    }
                }
            }
            task buildExecutables {
                dependsOn binaries.withType(ExecutableBinary).matching {
                    it.buildable
                }
            }
"""

        and:
        succeeds "buildExecutables"

        then:
        executable("build/binaries/mainExecutable/x86/main").binaryInfo.arch.name == "x86"
        executable("build/binaries/mainExecutable/x86/main").exec().out == "i386 ${os.familyName}"
        binaryInfo(objectFile("build/objectFiles/mainExecutable/x86/mainCpp/main")).arch.name == "x86"

        // x86_64 binaries not supported on MinGW or cygwin
        if (toolChain.id == "mingw" || toolChain.id == "gcccygwin") {
            executable("build/binaries/mainExecutable/x86_64/main").assertDoesNotExist()
        } else {
            executable("build/binaries/mainExecutable/x86_64/main").binaryInfo.arch.name == "x86_64"
            executable("build/binaries/mainExecutable/x86_64/main").exec().out == "amd64 ${os.familyName}"
            binaryInfo(objectFile("build/objectFiles/mainExecutable/x86_64/mainCpp/main")).arch.name == "x86_64"
        }

        // Itanium only supported on visualCpp
        if (toolChain.visualCpp) {
            executable("build/binaries/mainExecutable/itanium/main").binaryInfo.arch.name == "ia-64"
            binaryInfo(objectFile("build/objectFiles/mainExecutable/itanium/mainCpp/main")).arch.name == "ia-64"
        } else {
            executable("build/binaries/mainExecutable/itanium/main").assertDoesNotExist()
        }

        // ARM only supported on visualCpp 2013
        if (toolChain.visualCpp && toolChain.version == "2013") {
            executable("build/binaries/mainExecutable/arm/main").binaryInfo.arch.name == "arm"
            binaryInfo(objectFile("build/objectFiles/mainExecutable/arm/mainCpp/main")).arch.name == "arm"
        } else {
            executable("build/binaries/mainExecutable/arm/main").assertDoesNotExist()
        }
    }

    def "can configure binary for multiple target operating systems"() {
        when:
        buildFile << """
            model {
                platforms {
                    create("windows") {
                        operatingSystem "windows"
                    }
                    create("linux") {
                        operatingSystem "linux"
                    }
                    create("osx") {
                        operatingSystem "osx"
                    }
                }
            }

            binaries.matching({ it.targetPlatform.operatingSystem.windows }).all {
                cppCompiler.define "FRENCH"
            }
            task buildExecutables {
                dependsOn binaries.withType(ExecutableBinary).matching {
                    it.buildable
                }
            }
        """

        and:
        succeeds "buildExecutables"

        then:
        if (os.windows) {
            executable("build/binaries/mainExecutable/windows/main").exec().out == "amd64 windows"
        } else if (os.linux) {
            executable("build/binaries/mainExecutable/linux/main").exec().out == "amd64 linux"
        } else if (os.macOsX) {
            executable("build/binaries/mainExecutable/osx/main").exec().out == "amd64 os x"
        } else {
            throw new AssertionError("Unexpected operating system")
        }
    }

    def "fails with reasonable error message when trying to build for an unavailable architecture"() {
        when:
        buildFile << """
            model {
                platforms {
                    create("sparc") {
                        architecture "sparc"
                    }
                }
            }
"""

        and:
        fails "mainExecutable"

        then:
        failure.assertHasDescription("Execution failed for task ':compileMainExecutableMainCpp'.")
        failure.assertHasCause("No tool chain is available to build for platform 'sparc': ${toolChain.instanceDisplayName} cannot build for platform 'sparc'.")
    }

    def "fails with reasonable error message when trying to build for a different operating system"() {
        when:
        buildFile << """
            model {
                platforms {
                    create("solaris") {
                        operatingSystem "solaris"
                    }
                }
            }
"""

        and:
        fails "mainExecutable"

        then:
        failure.assertHasDescription("Execution failed for task ':compileMainExecutableMainCpp'.")
        failure.assertHasCause("No tool chain is available to build for platform 'solaris': ${toolChain.instanceDisplayName} cannot build for platform 'solaris'.")
    }

    def "fails with reasonable error message when trying to target an unknown platform"() {
        when:
        settingsFile << "rootProject.name = 'bad-platform'"
        buildFile << """
            model {
                platforms {
                    create("main") {}
                }
            }
            executables.main.targetPlatforms "unknown"
"""

        and:
        fails "mainExecutable"

        then:
        failure.assertHasDescription("A problem occurred configuring root project 'bad-platform'.")
        failure.assertHasCause("Invalid Platform: 'unknown'")
    }

    def binaryInfo(TestFile file) {
        file.assertIsFile()
        if (os.macOsX) {
            return new OtoolBinaryInfo(file)
        }
        if (os.windows) {
            return new DumpbinBinaryInfo(file, toolChain)
        }
        return new ReadelfBinaryInfo(file)
    }

}
