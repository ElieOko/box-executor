package com.appbox.runtime.service.auth

import com.appbox.runtime.core.contract.AuthServiceContract
import com.appbox.runtime.core.contract.PermissionContract
import com.appbox.runtime.core.contract.RemoteMonitorContract
import com.appbox.runtime.core.model.AuthSession
import com.appbox.runtime.core.model.MonitorEventType
import com.appbox.runtime.core.model.RemoteMonitorEvent
import com.appbox.runtime.core.model.RuntimePermission
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class AuthService(
    private val permissions: PermissionContract,
    private val remoteMonitor: RemoteMonitorContract,
) : AuthServiceContract {

    private val mutex = Mutex()
    private val sessions = mutableMapOf<String, AuthSession>()

    override suspend fun authenticate(
        packageName: String,
        credentials: Map<String, String>,
    ): Result<AuthSession> = mutex.withLock {
        runCatching {
            if (!permissions.hasPermission(packageName, RuntimePermission.AUTH_WRITE)) {
                throw SecurityException("AUTH_WRITE not granted for $packageName")
            }

            val userId = credentials["user"] ?: credentials["username"]
                ?: throw IllegalArgumentException("Missing user credential")
            val password = credentials["password"]
                ?: throw IllegalArgumentException("Missing password credential")

            if (password.isBlank()) {
                throw IllegalArgumentException("Invalid credentials")
            }

            val session = AuthSession(
                sessionId = UUID.randomUUID().toString(),
                userId = userId,
                packageName = packageName,
                accessToken = UUID.randomUUID().toString(),
                expiresAt = System.currentTimeMillis() + SESSION_DURATION_MS,
                scopes = setOf("read", "write"),
            )
            sessions[session.sessionId] = session

            remoteMonitor.report(
                RemoteMonitorEvent(
                    type = MonitorEventType.AUTH_EVENT,
                    packageName = packageName,
                    message = "User authenticated: $userId",
                ),
            )
            session
        }
    }

    override suspend fun validateSession(sessionId: String): AuthSession? = mutex.withLock {
        val session = sessions[sessionId] ?: return null
        if (session.expiresAt < System.currentTimeMillis()) {
            sessions.remove(sessionId)
            return null
        }
        session
    }

    override suspend fun revokeSession(sessionId: String): Result<Unit> = mutex.withLock {
        runCatching {
            sessions.remove(sessionId)
            Unit
        }
    }

    override suspend fun refreshSession(sessionId: String): Result<AuthSession> = mutex.withLock {
        runCatching {
            val session = sessions[sessionId]
                ?: throw IllegalArgumentException("Session not found")
            val refreshed = session.copy(
                accessToken = UUID.randomUUID().toString(),
                expiresAt = System.currentTimeMillis() + SESSION_DURATION_MS,
            )
            sessions[sessionId] = refreshed
            refreshed
        }
    }

    companion object {
        private const val SESSION_DURATION_MS = 3_600_000L
    }
}
