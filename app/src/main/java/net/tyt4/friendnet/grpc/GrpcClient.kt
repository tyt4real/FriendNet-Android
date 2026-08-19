package net.tyt4.friendnet.grpc

import android.net.LocalSocket
import android.net.LocalSocketAddress
import io.grpc.CallCredentials
import io.grpc.ConnectivityState
import io.grpc.ManagedChannel
import io.grpc.Metadata
import io.grpc.okhttp.OkHttpChannelBuilder
import net.tyt4.friendnet.gen.ClientRpcServiceGrpc
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.Socket
import java.net.SocketAddress
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory
import kotlinx.coroutines.delay

class GrpcClient private constructor(socketPath: String) {
    private val channel: ManagedChannel = OkHttpChannelBuilder
        .forTarget("127.0.0.1:1234")
        .socketFactory(UnixDomainSocketFactory(socketPath))
        .usePlaintext()
        .build()

    private val tokenCredentials = object : CallCredentials() {
        override fun applyRequestMetadata(requestInfo: RequestInfo, appExecutor: Executor, applier: MetadataApplier) {
            appExecutor.execute {
                try {
                    val metadata = Metadata()
                    val authKey = Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER)
                    synchronized(this@GrpcClient) {
                        bearerToken?.let {
                            metadata.put(authKey, "Bearer $it")
                        }
                    }
                    applier.apply(metadata)
                } catch (e: Exception) {
                    applier.fail(io.grpc.Status.UNAUTHENTICATED.withCause(e))
                }
            }
        }

        override fun thisUsesUnstableApi() {}
    }

    val asyncStub: ClientRpcServiceGrpc.ClientRpcServiceStub = ClientRpcServiceGrpc.newStub(channel).withCallCredentials(tokenCredentials)
    val blockingStub: ClientRpcServiceGrpc.ClientRpcServiceBlockingStub = ClientRpcServiceGrpc.newBlockingStub(channel).withCallCredentials(tokenCredentials)
    val futureStub: ClientRpcServiceGrpc.ClientRpcServiceFutureStub = ClientRpcServiceGrpc.newFutureStub(channel).withCallCredentials(tokenCredentials)

    private var bearerToken: String? = null

    companion object {
        @Volatile
        private var instance: GrpcClient? = null
        private var globalSocketPath: String? = null

        fun init(socketPath: String) {
            globalSocketPath = socketPath
        }

        fun getInstance(): GrpcClient {
            return instance ?: synchronized(this) {
                val path = globalSocketPath ?: throw IllegalStateException("GrpcClient not initialized. Call init(socketPath) first.")
                instance ?: GrpcClient(path).also { instance = it }
            }
        }

        fun setToken(token: String) {
            getInstance().apply {
                synchronized(this) {
                    bearerToken = token
                }
            }
        }
    }

    suspend fun waitUntilReady(timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val state = channel.getState(true)
            if (state == ConnectivityState.READY) {
                synchronized(this) {
                    if (bearerToken != null) return true
                }
            }
            delay(1500)
        }
        return false
    }

    fun shutdown() {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
    }
}

private class UnixDomainSocketFactory(private val path: String) : SocketFactory() {
    override fun createSocket(): Socket = UnixDomainSocket(path)

    override fun createSocket(host: String?, port: Int): Socket {
        return createSocket().apply { connect(null) }
    }

    override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket {
        return createSocket().apply { connect(null) }
    }

    override fun createSocket(host: InetAddress?, port: Int): Socket {
        return createSocket().apply { connect(null) }
    }

    override fun createSocket(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): Socket {
        return createSocket().apply { connect(null) }
    }
}

private class UnixDomainSocket(private val path: String) : Socket() {
    private val localSocket = LocalSocket()

    override fun connect(endpoint: SocketAddress?) {
        connect(endpoint, 0)
    }

    override fun connect(endpoint: SocketAddress?, timeout: Int) {
        localSocket.connect(LocalSocketAddress(path, LocalSocketAddress.Namespace.FILESYSTEM))
    }

    override fun getInputStream(): InputStream = localSocket.inputStream
    override fun getOutputStream(): OutputStream = localSocket.outputStream
    override fun isConnected(): Boolean = localSocket.isConnected
    override fun isClosed(): Boolean = !localSocket.isConnected && !localSocket.isBound
    override fun close() = localSocket.close()

    override fun setSoTimeout(timeout: Int) {
        localSocket.soTimeout = timeout
    }

    override fun getSoTimeout(): Int = localSocket.soTimeout

    override fun setTcpNoDelay(on: Boolean) {}
    override fun getTcpNoDelay(): Boolean = true
    override fun setKeepAlive(on: Boolean) {}
    override fun getKeepAlive(): Boolean = true
}
