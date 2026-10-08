package com.hiddify.hiddify.utils

/*
 * Copyright (C) 2019 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.hiddify.hiddify.Settings
import com.squareup.wire.GrpcClient
import okhttp3.OkHttpClient
import okhttp3.Protocol
import java.util.concurrent.TimeUnit

object GrpcClientProvider {
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain -> chain.proceed(chain.request().newBuilder()
            .header("authorization", "Bearer ${Settings.grpcAuthToken}").build()) }
        .protocols(listOf(Protocol.H2_PRIOR_KNOWLEDGE))
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    // A selected-outbound probe can legitimately take 30 seconds on an adaptive network.
    // Keep this client separate from the shorter deadlines used for normal UI RPCs.
    private val diagnosticsOkHttpClient = okHttpClient.newBuilder()
        .readTimeout(35, TimeUnit.SECONDS)
        .build()
    private var cachedDiagnosticsPort = -1
    private var cachedDiagnosticsClient: GrpcClient? = null
    val diagnosticsGrpcClient: GrpcClient
        @Synchronized get() {
            val port = Settings.grpcServiceModePort
            if (cachedDiagnosticsClient == null || cachedDiagnosticsPort != port) {
                cachedDiagnosticsClient = GrpcClient.Builder().client(diagnosticsOkHttpClient)
                    .baseUrl("http://127.0.0.1:$port").build()
                cachedDiagnosticsPort = port
            }
            return cachedDiagnosticsClient!!
        }

    private var cachedPort = -1
    private var cachedClient: GrpcClient? = null
    val grpcClient: GrpcClient
        @Synchronized get() {
            val port = Settings.grpcServiceModePort
            if (cachedClient == null || cachedPort != port) {
                cachedClient = GrpcClient.Builder().client(okHttpClient)
                    .baseUrl("http://127.0.0.1:$port").build()
                cachedPort = port
            }
            return cachedClient!!
        }
}
