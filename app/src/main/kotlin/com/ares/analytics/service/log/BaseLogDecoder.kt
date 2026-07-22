package com.ares.analytics.service.log

import com.ares.analytics.service.FrameBatcher
import java.io.File

abstract class BaseLogDecoder {
    abstract suspend fun decode(
        file: File,
        sessionId: String,
        batcher: FrameBatcher
    )
}
