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

package org.panda_lang.reposilite.repository;

import org.apache.http.HttpStatus;
import org.panda_lang.reposilite.Reposilite;
import org.panda_lang.reposilite.ReposiliteContext;
import org.panda_lang.reposilite.auth.Authenticator;
import org.panda_lang.reposilite.auth.Session;
import org.panda_lang.reposilite.error.ErrorDto;
import org.panda_lang.reposilite.error.ResponseUtils;
import org.panda_lang.reposilite.metadata.MetadataService;
import org.panda_lang.reposilite.utils.Result;

import java.io.File;
import java.util.concurrent.CompletableFuture;

public final class DeployService {

    protected static final int RETRY_WRITE_TIME = 1000;

    private final boolean deployEnabled;
    private final Authenticator authenticator;
    private final RepositoryService repositoryService;
    private final MetadataService metadataService;

    public DeployService(
            boolean deployEnabled,
            Authenticator authenticator,
            RepositoryService repositoryService,
            MetadataService metadataService) {

        this.deployEnabled = deployEnabled;
        this.authenticator = authenticator;
        this.repositoryService = repositoryService;
        this.metadataService = metadataService;
    }

    public Result<CompletableFuture<Result<FileDetailsDto, ErrorDto>>, ErrorDto> deploy(ReposiliteContext context) {
        if (!deployEnabled) {
            return ResponseUtils.error(HttpStatus.SC_METHOD_NOT_ALLOWED, "Artifact deployment is disabled");
        }

        Result<Session, String> authResult = this.authenticator.authByUri(context.headers(), context.uri());

        if (authResult.containsError()) {
            return ResponseUtils.error(HttpStatus.SC_UNAUTHORIZED, authResult.getError());
        }

        if (!repositoryService.getDiskQuota().hasUsableSpace()) {
            return ResponseUtils.error(HttpStatus.SC_INSUFFICIENT_STORAGE, "Out of disk space");
        }

        File file = repositoryService.getFile(context.uri());
        FileDetailsDto fileDetails = FileDetailsDto.of(file);

        File metadataFile = new File(file.getParentFile(), "maven-metadata.xml");
        metadataService.clearMetadata(metadataFile);

        Reposilite.getLogger().info("DEPLOY " + authResult.getValue().getAlias() + " successfully deployed " + file + " from " + context.address());

        if (file.getName().contains("maven-metadata")) {
            return Result.ok(CompletableFuture.completedFuture(Result.ok(fileDetails)));
        }

        CompletableFuture<Result<FileDetailsDto, ErrorDto>> task = repositoryService.storeFile(
                context.uri(),
                file,
                context::input,
                () -> fileDetails,
                exception -> new ErrorDto(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Failed to upload artifact"));

        return Result.ok(task);
    }

}
