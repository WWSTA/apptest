package com.motrix.android.core.engine

import android.content.Context
import com.motrix.android.core.engine.model.EngineConfig
import com.motrix.android.core.engine.model.ProcessState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Aria2ProcessManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var aria2Process: Process? = null
    private val _processState = MutableStateFlow(ProcessState.IDLE)
    val processState: Flow<ProcessState> = _processState.asStateFlow()

    private var processMonitorThread: Thread? = null

    private val aria2Dir = File(context.applicationInfo.dataDir, "aria2")
    private val aria2Binary = File(aria2Dir, "aria2c")
    private val sessionFile = File(aria2Dir, "aria2.session")

    suspend fun startAria2Process(config: EngineConfig): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (isAria2Running()) {
                Timber.w("aria2c process is already running")
                return@runCatching
            }

            _processState.value = ProcessState.STARTING

            extractBinaryIfNeeded()
            ensureSessionFile()

            val command = buildCommandArgs(config)
            Timber.i("Starting aria2c with command: ${command.joinToString(" ")}")

            val processBuilder = ProcessBuilder(command)
                .directory(aria2Dir)
                .redirectErrorStream(true)

            val process = processBuilder.start()
            aria2Process = process

            startProcessMonitor(process)

            Thread {
                try {
                    val reader = BufferedReader(InputStreamReader(process.inputStream))
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        Timber.d("aria2c: $line")
                    }
                } catch (e: Exception) {
                    if (isActive) {
                        Timber.e(e, "Error reading aria2c output")
                    }
                }
            }.start()

            Thread.sleep(500)

            if (!process.isAlive) {
                val exitCode = process.exitValue()
                _processState.value = ProcessState.CRASHED
                throw IllegalStateException("aria2c process exited immediately with code: $exitCode")
            }

            _processState.value = ProcessState.RUNNING
            Timber.i("aria2c process started successfully")
        }.onFailure { e ->
            _processState.value = ProcessState.CRASHED
            Timber.e(e, "Failed to start aria2c process")
        }
    }

    suspend fun stopAria2Process(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val process = aria2Process ?: run {
                Timber.w("aria2c process is not running")
                _processState.value = ProcessState.IDLE
                return@runCatching
            }

            if (!process.isAlive) {
                aria2Process = null
                _processState.value = ProcessState.STOPPED
                return@runCatching
            }

            try {
                val outputStream: OutputStream = process.outputStream
                outputStream.write("aria2.shutdown()\n".toByteArray())
                outputStream.flush()
            } catch (e: Exception) {
                Timber.w(e, "Failed to send shutdown command via stdin")
            }

            val exited = try {
                val completed = process.waitFor()
                Timber.i("aria2c exited with code: $completed")
                true
            } catch (e: InterruptedException) {
                false
            }

            if (!exited) {
                Timber.w("aria2c did not exit gracefully, force killing")
                process.destroy()
                try {
                    process.waitFor()
                } catch (_: InterruptedException) {
                    process.destroyForcibly()
                }
            }

            aria2Process = null
            processMonitorThread = null
            _processState.value = ProcessState.STOPPED
            Timber.i("aria2c process stopped")
        }
    }

    fun isAria2Running(): Boolean {
        val process = aria2Process ?: return false
        return process.isAlive
    }

    fun observeProcessState(): Flow<ProcessState> = _processState.asStateFlow()

    private fun extractBinaryIfNeeded() {
        if (aria2Binary.exists() && aria2Binary.canExecute()) {
            Timber.d("aria2c binary already exists at ${aria2Binary.absolutePath}")
            return
        }

        if (!aria2Dir.exists()) {
            aria2Dir.mkdirs()
        }

        val abi = getAbi()
        val assetPath = "aria2/$abi/aria2c"

        try {
            context.assets.open(assetPath).use { input ->
                aria2Binary.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            throw IllegalStateException("Failed to extract aria2c binary from assets/$assetPath", e)
        }

        val chmodResult = Runtime.getRuntime().exec(
            arrayOf("chmod", "755", aria2Binary.absolutePath)
        )
        chmodResult.waitFor()

        if (chmodResult.exitValue() != 0) {
            throw IllegalStateException("Failed to chmod aria2c binary")
        }

        Timber.i("aria2c binary extracted to ${aria2Binary.absolutePath}")
    }

    private fun ensureSessionFile() {
        if (!sessionFile.exists()) {
            sessionFile.createNewFile()
            Timber.i("Created empty session file at ${sessionFile.absolutePath}")
        }
    }

    private fun buildCommandArgs(config: EngineConfig): List<String> {
        val args = mutableListOf<String>()

        args.add(aria2Binary.absolutePath)
        args.add("--enable-rpc")
        args.add("--rpc-listen-port=${RPC_PORT}")
        args.add("--rpc-listen-all=true")

        if (config.rpcSecret.isNotEmpty()) {
            args.add("--rpc-secret=${config.rpcSecret}")
        }

        args.add("--dir=${config.downloadDir}")
        args.add("--max-concurrent-downloads=${config.maxConcurrentDownloads}")
        args.add("--max-connection-per-server=${config.maxConnectionPerServer}")
        args.add("--split=${config.split}")

        if (config.maxOverallDownloadLimit != "0") {
            args.add("--max-overall-download-limit=${config.maxOverallDownloadLimit}")
        }
        if (config.maxDownloadLimit != "0") {
            args.add("--max-download-limit=${config.maxDownloadLimit}")
        }

        if (config.continueDownload) {
            args.add("--continue=true")
        }

        if (config.enableDht) {
            args.add("--enable-dht=true")
        }
        if (config.btEnableLpd) {
            args.add("--bt-enable-lpd=true")
        }
        if (config.btEnablePeerExchange) {
            args.add("--enable-peer-exchange=true")
        }

        args.add("--listen-port=${config.listenPort}-${config.listenPort + 9}")
        args.add("--dht-listen-port=${config.listenPort + 10}-${config.listenPort + 19}")

        args.add("--save-session=${sessionFile.absolutePath}")
        args.add("--save-session-interval=60")
        args.add("--input-file=${sessionFile.absolutePath}")

        args.add("--auto-save-interval=60")
        args.add("--file-allocation=falloc")
        args.add("--disk-cache=32M")

        if (config.userAgent.isNotEmpty()) {
            args.add("--user-agent=${config.userAgent}")
        }

        args.add("--quiet=true")

        return args
    }

    private fun startProcessMonitor(process: Process) {
        processMonitorThread = Thread({
            try {
                val exitCode = process.waitFor()
                Timber.w("aria2c process exited with code: $exitCode")
                if (_processState.value == ProcessState.RUNNING) {
                    _processState.value = ProcessState.CRASHED
                }
            } catch (e: InterruptedException) {
                Timber.d("Process monitor interrupted")
            }
        }, "aria2c-process-monitor").apply {
            isDaemon = true
            start()
        }
    }

    private fun getAbi(): String {
        return if (android.os.Build.SUPPORTED_ABIS.isNotEmpty()) {
            android.os.Build.SUPPORTED_ABIS[0]
        } else {
            android.os.Build.CPU_ABI
        }
    }

    companion object {
        const val RPC_PORT = 6800
    }
}
