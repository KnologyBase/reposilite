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

package org.panda_lang.reposilite.auth

import groovy.transform.CompileStatic
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import static org.junit.jupiter.api.Assertions.*

@CompileStatic
class TokenServiceTest {

    @TempDir
    protected File workingDirectory
    protected TokenService tokenService

    @BeforeEach
    void prepare() {
        this.tokenService = new TokenService(workingDirectory.getAbsolutePath())
    }

    @Test
    void 'should save and load' () {
        def tempService = new TokenService(workingDirectory.getAbsolutePath())
        tempService.createToken("path", "alias")
        tempService.saveTokens()

        tokenService.loadTokens() // uses the same file
        Token token = tokenService.getToken("alias")
        assertEquals "path", token.getPath()
    }

    @Test
    void 'should create token' () {
        def result = tokenService.createToken("path", "alias")
        assertNotNull tokenService.getToken("alias")

        def token = result.getValue()
        assertEquals "path", token.getPath()
        assertEquals "alias", token.getAlias()
        assertTrue TokenService.B_CRYPT_TOKENS_ENCODER.matches(result.getKey(), token.getToken())

        def customResult = tokenService.createToken("custom_path", "custom_alias", "secret")
        assertNotNull tokenService.getToken("custom_alias")

        def customToken = customResult.getValue()
        assertEquals "custom_path", customToken.getPath()
        assertEquals "custom_alias", customToken.getAlias()
        assertTrue TokenService.B_CRYPT_TOKENS_ENCODER.matches("secret", customToken.getToken())
    }

    @Test
    void 'should add token' () {
        def token = new Token("path", "alias", "secret")
        tokenService.addToken(token)
        assertEquals token, tokenService.getToken("alias")
    }

    @Test
    void 'should delete token' () {
        assertNull(tokenService.deleteToken("random"))

        tokenService.createToken("path", "alias", "token")
        Token token = tokenService.deleteToken("alias")
        assertNotNull(token)
        assertEquals("alias", token.getAlias())

        assertNull(tokenService.getToken("alias"))
    }

    @Test
    void 'should get token' () {
        assertNull tokenService.getToken("random")
        tokenService.createToken("path", "alias")
        assertNotNull tokenService.getToken("alias")
    }

    @Test
    void 'should count tokens' () {
        assertEquals 0, tokenService.count()

        tokenService.createToken("a", "a")
        tokenService.createToken("b", "b")
        assertEquals 2, tokenService.count()

        tokenService.deleteToken("a")
        assertEquals 1, tokenService.count()
    }

    @Test
    void 'should get all tokens' () {
        assertIterableEquals Collections.emptyList(), tokenService.getTokens()

        def token = tokenService.createToken("path", "alias")
        assertIterableEquals Collections.singletonList(token.getValue()), tokenService.getTokens()
    }

}