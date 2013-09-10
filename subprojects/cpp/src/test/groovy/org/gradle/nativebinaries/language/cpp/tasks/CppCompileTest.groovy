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

package org.gradle.nativebinaries.language.cpp.tasks
import org.gradle.api.internal.tasks.compile.Compiler
import org.gradle.api.tasks.WorkResult
import org.gradle.nativebinaries.internal.ToolChainInternal
import org.gradle.nativebinaries.language.cpp.internal.CppCompileSpec
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import spock.lang.Specification

class CppCompileTest extends Specification {
    def testDir = new TestNameTestDirectoryProvider().testDirectory
    CppCompile cppCompile = TestUtil.createTask(CppCompile)
    def toolChain = Mock(ToolChainInternal)
    Compiler<CppCompileSpec> cppCompiler = Mock(Compiler)

    def "executes using the CppCompiler"() {
        def inputDir = testDir.file("sourceFile")
        def result = Mock(WorkResult)
        when:
        cppCompile.toolChain = toolChain
        cppCompile.compilerArgs = ["arg"]
        cppCompile.macros = [def: "value"]
        cppCompile.objectFileDir = testDir.file("outputFile")
        cppCompile.source inputDir
        cppCompile.execute()

        then:
        _ * toolChain.outputType >> "cpp"
        1 * toolChain.createCppCompiler() >> cppCompiler
        1 * cppCompiler.execute({ CppCompileSpec spec ->
            assert spec.source.collect {it.name} == ["sourceFile"]
            assert spec.args == ['arg']
            assert spec.macros == [def: 'value']
            assert spec.objectFileDir.name == "outputFile"
            true
        }) >> result
        1 * result.didWork >> true
        0 * _._

        and:
        cppCompile.didWork
    }
}