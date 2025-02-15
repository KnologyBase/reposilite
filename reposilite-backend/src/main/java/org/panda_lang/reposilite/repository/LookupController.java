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

import io.javalin.http.Context;
import io.javalin.http.Handler;
import org.apache.http.HttpStatus;
import org.panda_lang.reposilite.Reposilite;
import org.panda_lang.reposilite.ReposiliteContext;
import org.panda_lang.reposilite.ReposiliteContextFactory;
import org.panda_lang.reposilite.config.Configuration;
import org.panda_lang.reposilite.error.ErrorDto;
import org.panda_lang.reposilite.error.FailureService;
import org.panda_lang.reposilite.frontend.FrontendService;
import org.panda_lang.reposilite.utils.Result;

import java.util.concurrent.ExecutorService;

public final class LookupController implements Handler {

    private final boolean hasProxied;
    private final ReposiliteContextFactory contextFactory;
    private final FrontendService frontend;
    private final LookupService lookupService;
    private final ProxyService proxyService;

    public LookupController(
            Configuration configuration,
            ReposiliteContextFactory contextFactory,
            ExecutorService executorService,
            FrontendService frontendService,
            LookupService lookupService,
            RepositoryService repositoryService,
            FailureService failureService) {

        this.contextFactory = contextFactory;
        this.frontend = frontendService;
        this.lookupService = lookupService;
        this.hasProxied = configuration.proxied.size() > 0;

        this.proxyService = new ProxyService(
                configuration.storeProxied,
                configuration.rewritePathsEnabled,
                configuration.proxied,
                executorService,
                failureService,
                repositoryService);
    }

    @Override
    public void handle(Context ctx) {
        ReposiliteContext context = contextFactory.create(ctx);
        Reposilite.getLogger().info("LOOKUP " + context.uri() + " from " + context.address());

        Result<LookupResponse, ErrorDto> lookupResponse = lookupService.findLocal(context);

        if (isProxied(lookupResponse)) {
            if (hasProxied) {
                Result<?, ?> proxiedResult = proxyService.findProxied(context)
                        .map(future -> future.thenAccept(result -> handleResult(ctx, result)));

                if (proxiedResult.isDefined()) {
                    return;
                }
            }

            if (isProxied(lookupResponse)) {
                lookupResponse = lookupResponse.mapError(proxiedError -> new ErrorDto(HttpStatus.SC_NOT_FOUND, proxiedError.getMessage()));
            }
        }

        handleResult(ctx, lookupResponse);
    }

    private void handleResult(Context ctx, Result<LookupResponse, ErrorDto> result) {
        result.peek(response -> {
            response.getFileDetails().peek(details -> {
                if (details.getContentLength() > 0) {
                    ctx.res.setContentLengthLong(details.getContentLength());
                }

                if (response.isAttachment()) {
                    ctx.res.setHeader("Content-Disposition", "attachment; filename=\"" + details.getName() + "\"");
                }
            });

            response.getContentType().peek(ctx.res::setContentType);
            response.getValue().peek(ctx::result);
        })
        .onError(error -> ctx
                .status(error.getStatus())
                .contentType("text/html")
                .result(frontend.forMessage(error.getMessage()))
                .res.setCharacterEncoding("UTF-8")
        );
    }

    private boolean isProxied(Result<LookupResponse, ErrorDto> lookupResponse) {
        return lookupResponse.containsError() && lookupResponse.getError().getStatus() == HttpStatus.SC_USE_PROXY;
    }

}
