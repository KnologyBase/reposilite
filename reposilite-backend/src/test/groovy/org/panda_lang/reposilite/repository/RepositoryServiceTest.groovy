/*
 * Copyright (c) 2020 Dzikoysk
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

package org.panda_lang.reposilite.repository

import groovy.transform.CompileStatic
import org.junit.jupiter.api.Test
import org.panda_lang.reposilite.ReposiliteContext
import org.panda_lang.reposilite.ReposiliteTestSpecification
import org.panda_lang.utilities.commons.FileUtils

import java.nio.channels.FileChannel
import java.nio.file.OpenOption
import java.nio.file.StandardOpenOption

import static org.junit.jupiter.api.Assertions.assertTrue

@CompileStatic
class RepositoryServiceTest extends ReposiliteTestSpecification {

    @Test
    void 'should retry deployment of locked file' () {
        def repositoryService = super.reposilite.getRepositoryService()

        def file = new File(super.workingDirectory.getAbsolutePath() + '/releases/a/b/c.txt'.replace('/', File.separator))
        file.getParentFile().mkdirs()
        FileUtils.overrideFile(file, 'test')

        def channel = FileChannel.open(file.toPath(), [ StandardOpenOption.WRITE ] as OpenOption[])
        def lock = channel.lock()

        new Thread({
            Thread.sleep(1000L)
            lock.release()
        }).start()

        def context = new ReposiliteContext('/releases/a/b/c.txt', 'POST', '', [:], { new ByteArrayInputStream('test'.bytes) }, {})
        assertTrue(repositoryService.storeFile("id", file, { context.input() }, { new Object() }, {}).get().isDefined())
    }

}
