package net.tyt4.friendnet.grpc

import android.util.Log
import io.grpc.stub.StreamObserver
import net.tyt4.friendnet.gen.Event
import net.tyt4.friendnet.gen.StreamEventsRequest
import net.tyt4.friendnet.gen.StreamEventsResponse
import java.util.concurrent.CopyOnWriteArrayList

class EventStreamManager private constructor() {
    private val TAG = "EventStreamManager"
    private val listeners = mutableMapOf<Event.Type, CopyOnWriteArrayList<(Event) -> Unit>>()
    private val onConnectListeners = CopyOnWriteArrayList<() -> Unit>()
    private var isStarted = false

    companion object {
        @Volatile
        private var instance: EventStreamManager? = null

        fun getInstance(): EventStreamManager {
            return instance ?: synchronized(this) {
                instance ?: EventStreamManager().also { instance = it }
            }
        }
    }

    fun addListener(type: Event.Type, listener: (Event) -> Unit) {
        listeners.getOrPut(type) { CopyOnWriteArrayList() }.add(listener)
    }

    fun removeListener(type: Event.Type, listener: (Event) -> Unit) {
        listeners[type]?.remove(listener)
    }

    fun addOnConnectListener(listener: () -> Unit) {
        onConnectListeners.add(listener)
    }

    fun removeOnConnectListener(listener: () -> Unit) {
        onConnectListeners.remove(listener)
    }

    fun start() {
        if (isStarted) return
        isStarted = true
        connect()
    }

    private fun connect() {
        val request = StreamEventsRequest.getDefaultInstance()
        onConnectListeners.forEach { it() }
        GrpcClient.getInstance().asyncStub.streamEvents(request, object : StreamObserver<StreamEventsResponse> {
            override fun onNext(value: StreamEventsResponse) {
                val event = value.event
                Log.d(TAG, "Received event: ${event.type}")
                listeners[event.type]?.forEach { it(event) }
            }

            override fun onError(t: Throwable?) {
                Log.e(TAG, "Event stream error, reconnecting in 2s...", t)
                isStarted = false
                Thread.sleep(2000)
                start()
            }

            override fun onCompleted() {
                Log.i(TAG, "Event stream completed")
                isStarted = false
            }
        })
    }
}