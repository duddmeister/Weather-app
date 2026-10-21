package io.github.duddmeister.weather.core

/**
 * Tiny logging facade so the data and domain layers stay free of Android APIs (and unit-testable on
 * the JVM). The application installs an android.util.Log backed sink at startup.
 */
object AppLog {
    fun interface Sink {
        fun log(level: Char, tag: String, message: String, throwable: Throwable?)
    }

    @Volatile
    var sink: Sink = Sink { level, tag, message, throwable ->
        println("$level/$tag: $message")
        throwable?.printStackTrace()
    }

    fun d(tag: String, message: String) = sink.log('D', tag, message, null)
    fun i(tag: String, message: String) = sink.log('I', tag, message, null)
    fun w(tag: String, message: String, throwable: Throwable? = null) = sink.log('W', tag, message, throwable)
    fun e(tag: String, message: String, throwable: Throwable? = null) = sink.log('E', tag, message, throwable)
}
