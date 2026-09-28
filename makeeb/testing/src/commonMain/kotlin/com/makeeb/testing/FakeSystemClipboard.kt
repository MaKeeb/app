package com.makeeb.testing

import com.makeeb.platform.clipboard.Clip
import com.makeeb.platform.clipboard.SystemClipboard
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/** A clipboard the test copies to with [copy]; never touches a real one. */
class FakeSystemClipboard : SystemClipboard {
    private var current: Clip? = null
    private val flow = MutableSharedFlow<Clip>(extraBufferCapacity = 16)

    override fun read(): Clip? = current

    override fun write(text: String) = copy(Clip(text))

    override val changes: Flow<Clip> = flow

    fun copy(clip: Clip) {
        current = clip
        flow.tryEmit(clip)
    }
}
