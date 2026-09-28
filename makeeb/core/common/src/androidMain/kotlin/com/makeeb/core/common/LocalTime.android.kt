package com.makeeb.core.common

import java.time.LocalTime

actual fun currentMinuteOfDay(): Int = LocalTime.now().let { it.hour * 60 + it.minute }
