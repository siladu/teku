/*
 * Copyright 2020 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.teku.beaconrestapi.handlers.tekuv1.admin;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import static javax.servlet.http.HttpServletResponse.SC_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.CACHE_NONE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_TEKU;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TARGET_PEER_COUNT;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TARGET_PEER_COUNT_DESCRIPTION;
import static tech.pegasys.teku.infrastructure.restapi.endpoints.SingleQueryParameterUtils.getParameterValueAsLong;

import io.javalin.core.util.Header;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiParam;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.NetworkDataProvider;
import tech.pegasys.teku.api.SyncDataProvider;
import tech.pegasys.teku.beaconrestapi.schema.BadRequest;
import tech.pegasys.teku.provider.JsonProvider;

public class Readiness implements Handler {
  public static final String ROUTE = "/teku/v1/admin/readiness";
  private final SyncDataProvider syncProvider;
  private final ChainDataProvider chainDataProvider;
  private final NetworkDataProvider networkDataProvider;
  private final JsonProvider jsonProvider;

  public Readiness(final DataProvider provider, final JsonProvider jsonProvider) {
    this(
        provider.getSyncDataProvider(),
        provider.getChainDataProvider(),
        provider.getNetworkDataProvider(),
        jsonProvider);
  }

  Readiness(
      final SyncDataProvider syncProvider,
      final ChainDataProvider chainDataProvider,
      final NetworkDataProvider networkDataProvider,
      final JsonProvider jsonProvider) {
    this.syncProvider = syncProvider;
    this.chainDataProvider = chainDataProvider;
    this.networkDataProvider = networkDataProvider;
    this.jsonProvider = jsonProvider;
  }

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.GET,
      summary = "Get node readiness",
      description = "Returns 200 if the node is ready to accept traffic",
      tags = {TAG_TEKU},
      queryParams = {
        @OpenApiParam(name = TARGET_PEER_COUNT, description = TARGET_PEER_COUNT_DESCRIPTION),
      },
      responses = {
        @OpenApiResponse(status = RES_OK, description = "Node is ready"),
        @OpenApiResponse(
            status = RES_BAD_REQUEST,
            description = "Cannot parse " + TARGET_PEER_COUNT + " parameter passed in request"),
        @OpenApiResponse(
            status = RES_SERVICE_UNAVAILABLE,
            description = "Node not initialized or having issues")
      })
  @Override
  public void handle(final Context ctx) throws Exception {
    ctx.header(Header.CACHE_CONTROL, CACHE_NONE);
    final OptionalLong targetPeerCount;
    try {
      targetPeerCount = parseTargetPeerCountParameter(ctx);
    } catch (final IllegalArgumentException e) {
      ctx.status(SC_BAD_REQUEST);
      ctx.json(BadRequest.badRequest(jsonProvider, "Invalid " + TARGET_PEER_COUNT));
      return;
    }
    if (!chainDataProvider.isStoreAvailable()
        || syncProvider.isSyncing()
        || belowTargetPeerCount(targetPeerCount)) {
      ctx.status(SC_SERVICE_UNAVAILABLE);
    } else {
      ctx.status(SC_OK);
    }
  }

  private OptionalLong parseTargetPeerCountParameter(final Context ctx) {
    final Map<String, List<String>> parameters = ctx.queryParamMap();
    if (!parameters.containsKey(TARGET_PEER_COUNT)) {
      return OptionalLong.empty();
    }
    return OptionalLong.of(getParameterValueAsLong(parameters, TARGET_PEER_COUNT));
  }

  private boolean belowTargetPeerCount(final OptionalLong targetPeerCount) {
    return targetPeerCount.isPresent()
        && networkDataProvider.getPeerCount() < targetPeerCount.getAsLong();
  }
}
