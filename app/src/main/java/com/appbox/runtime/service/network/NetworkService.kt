package com.appbox.runtime.service.network

import com.appbox.runtime.core.contract.NetworkServiceContract
import com.appbox.runtime.core.contract.PermissionContract
import com.appbox.runtime.core.contract.RemoteMonitorContract
import com.appbox.runtime.core.model.MonitorEventType
import com.appbox.runtime.core.model.NetworkRequest
import com.appbox.runtime.core.model.NetworkResponse
import com.appbox.runtime.core.model.RemoteMonitorEvent
import com.appbox.runtime.core.model.RuntimePermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class NetworkService(
    private val permissions: PermissionContract,
    private val remoteMonitor: RemoteMonitorContract,
) : NetworkServiceContract {

    private val allowedDomains = setOf(
        "api.appbox.local",
        "localhost",
        "10.0.2.2",
    )

    override suspend fun execute(request: NetworkRequest): Result<NetworkResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (!permissions.hasPermission(request.packageName, RuntimePermission.NETWORK_ACCESS)) {
                    throw SecurityException("NETWORK_ACCESS not granted for ${request.packageName}")
                }
                if (!isAllowed(request.packageName, request.url)) {
                    throw SecurityException("URL not allowed: ${request.url}")
                }

                remoteMonitor.report(
                    RemoteMonitorEvent(
                        type = MonitorEventType.NETWORK_REQUEST,
                        packageName = request.packageName,
                        message = "${request.method} ${request.url}",
                    ),
                )

                val connection = URL(request.url).openConnection() as HttpURLConnection
                try {
                    connection.requestMethod = request.method
                    connection.connectTimeout = 10_000
                    connection.readTimeout = 10_000
                    request.headers.forEach { (key, value) ->
                        connection.setRequestProperty(key, value)
                    }
                    request.body?.let { body ->
                        connection.doOutput = true
                        connection.outputStream.use { it.write(body.toByteArray()) }
                    }

                    val statusCode = connection.responseCode
                    val body = try {
                        connection.inputStream.bufferedReader().readText()
                    } catch (_: Exception) {
                        connection.errorStream?.bufferedReader()?.readText()
                    }

                    NetworkResponse(
                        id = request.id,
                        statusCode = statusCode,
                        headers = connection.headerFields
                            .filterKeys { it != null }
                            .mapKeys { it.key!! }
                            .mapValues { it.value.joinToString(",") },
                        body = body,
                    )
                } finally {
                    connection.disconnect()
                }
            }
        }

    override suspend fun isAllowed(packageName: String, url: String): Boolean {
        if (!permissions.hasPermission(packageName, RuntimePermission.NETWORK_ACCESS)) {
            return false
        }
        return try {
            val host = URL(url).host
            allowedDomains.any { host == it || host.endsWith(".$it") }
        } catch (_: Exception) {
            false
        }
    }
}
