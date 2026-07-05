package com.ares.analytics.viewmodel

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.ByteArrayOutputStream

data class CameraStreamState(
    val streamUrl: String = "http://10.0.0.2:1181/stream.mjpg",
    val isConfiguring: Boolean = false,
    val currentFrame: ImageBitmap? = null,
    val isConnected: Boolean = false,
    val errorMessage: String? = null
)

sealed class CameraStreamIntent {
    data class SetConfiguring(val isConfiguring: Boolean) : CameraStreamIntent()
    data class UpdateStreamUrl(val streamUrl: String) : CameraStreamIntent()
    object Connect : CameraStreamIntent()
    object Disconnect : CameraStreamIntent()
}

class CameraStreamViewModel(
    initialStreamUrl: String?,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(
        CameraStreamState(
            streamUrl = initialStreamUrl ?: "http://10.0.0.2:1181/stream.mjpg",
            isConfiguring = initialStreamUrl == null
        )
    )
    val state: StateFlow<CameraStreamState> = _state.asStateFlow()
    
    private var streamJob: Job? = null
    
    init {
        if (initialStreamUrl != null) {
            startStreaming()
        }
    }
    
    fun onIntent(intent: CameraStreamIntent) {
        when (intent) {
            is CameraStreamIntent.SetConfiguring -> {
                _state.update { it.copy(isConfiguring = intent.isConfiguring) }
            }
            is CameraStreamIntent.UpdateStreamUrl -> {
                _state.update { it.copy(streamUrl = intent.streamUrl) }
            }
            is CameraStreamIntent.Connect -> {
                _state.update { it.copy(isConfiguring = false) }
                startStreaming()
            }
            is CameraStreamIntent.Disconnect -> {
                stopStreaming()
            }
        }
    }
    
    private fun stopStreaming() {
        streamJob?.cancel()
        streamJob = null
        _state.update { it.copy(isConnected = false, currentFrame = null) }
    }
    
    private fun startStreaming() {
        streamJob?.cancel()
        
        streamJob = scope.launch(Dispatchers.IO) {
            var retryDelayMs = 1000L
            while (isActive) {
                val currentUrl = _state.value.streamUrl
                if (_state.value.isConfiguring || currentUrl.isBlank()) {
                    delay(1000)
                    continue
                }
                
                _state.update { it.copy(isConnected = false, errorMessage = null) }

                val client = HttpClient(CIO) {
                    install(HttpTimeout) {
                        requestTimeoutMillis = Long.MAX_VALUE
                        socketTimeoutMillis = Long.MAX_VALUE
                    }
                }

                try {
                    client.prepareGet(currentUrl).execute { response ->
                        if (response.status.value in 200..299) {
                            _state.update { it.copy(isConnected = true) }
                            retryDelayMs = 1000L
                            val channel = response.bodyAsChannel()
                            val bos = ByteArrayOutputStream()
                            val readBuffer = ByteArray(8192)

                            while (isActive && !channel.isClosedForRead && !_state.value.isConfiguring) {
                                val read = channel.readAvailable(readBuffer, 0, readBuffer.size)
                                if (read > 0) {
                                    bos.write(readBuffer, 0, read)
                                    val currentBytes = bos.toByteArray()
                                    
                                    var soi = -1
                                    var eoi = -1
                                    for (i in 0 until currentBytes.size - 1) {
                                        if (currentBytes[i] == 0xFF.toByte() && currentBytes[i + 1] == 0xD8.toByte()) {
                                            soi = i
                                        }
                                        if (soi != -1 && currentBytes[i] == 0xFF.toByte() && currentBytes[i + 1] == 0xD9.toByte()) {
                                            eoi = i + 1
                                            break
                                        }
                                    }

                                    if (soi != -1 && eoi != -1 && eoi > soi) {
                                        val frameBytes = currentBytes.copyOfRange(soi, eoi + 1)
                                        val remainder = currentBytes.copyOfRange(eoi + 1, currentBytes.size)
                                        bos.reset()
                                        bos.write(remainder)

                                        try {
                                            val imageBitmap = org.jetbrains.skia.Image.makeFromEncoded(frameBytes).toComposeImageBitmap()
                                            _state.update { it.copy(currentFrame = imageBitmap) }
                                        } catch (e: Exception) {
                                            // Ignore
                                        }
                                    } else if (currentBytes.size > 2_000_000) {
                                        bos.reset()
                                    }
                                }
                            }
                        } else {
                            _state.update { it.copy(errorMessage = "HTTP Error: ${response.status}") }
                        }
                    }
                } catch (e: Exception) {
                    _state.update { it.copy(isConnected = false, errorMessage = e.message ?: "Connection failed") }
                } finally {
                    client.close()
                    _state.update { it.copy(isConnected = false) }
                }
                
                if (isActive && !_state.value.isConfiguring) {
                    delay(retryDelayMs)
                    retryDelayMs = (retryDelayMs * 1.5).toLong().coerceAtMost(5000L)
                }
            }
        }
    }
}
