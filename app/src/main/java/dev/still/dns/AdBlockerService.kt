package dev.still.dns

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.OsConstants
import android.system.Os
import android.system.ErrnoException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlinx.coroutines.*

class AdBlockerService : VpnService() {
    companion object {
        const val STOP = "dev.still.dns.STOP"
        const val RELOAD = "dev.still.dns.RELOAD"
        val BLOCKLIST = FilterPolicy.ads
    }
    private var session: Session? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var startup: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) { stopProtection(); return START_NOT_STICKY }
        if (intent?.action == RELOAD) {
            session?.reloadSettings()
            if (session == null && startup?.isActive != true) stopSelf()
            return START_NOT_STICKY
        }
        if (session != null || startup?.isActive == true) return START_NOT_STICKY
        ProtectionStore.update { it.copy(connection = Connection.Connecting, error = null) }
        try {
            foreground()
            startup = serviceScope.launch {
              try {
                // Load validated cached filters on an I/O worker before capturing DNS.
                FilterLibrary.get(this@AdBlockerService).load()
            val tunnel = Builder().setSession("Still DNS protection")
                // The DNS endpoint must not be a local interface address, or the
                // kernel can deliver queries locally instead of through the TUN.
                .setMtu(1500).addAddress("10.0.0.1", 32).addDnsServer("10.0.0.2")
                // DNS-only split route. A default route requires a complete TCP/IP forwarding stack.
                .addRoute("10.0.0.2", 32).allowFamily(OsConstants.AF_INET6)
                .setBlocking(false).setMeteredCompat()
                .establish() ?: error("VPN permission was revoked")
            val current = Session(tunnel)
            session = current
            ProtectionStore.update { it.copy(connection = Connection.Connected, lastDnsMillis = null, consecutiveFailures = 0) }
            current.start()
              } catch (cancelled: CancellationException) {
                  throw cancelled
              } catch (_: Exception) {
                  stopProtection("Unable to load filters or start the DNS tunnel. Try again.")
              }
            }
        } catch (_: Exception) {
            stopProtection("Unable to establish the local VPN. Check VPN permission and try again.")
        }
        return START_NOT_STICKY
    }

    private fun Builder.setMeteredCompat(): Builder {
        if (Build.VERSION.SDK_INT >= 29) setMetered(false)
        return this
    }

    private fun foreground() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("protection", "DNS protection", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 1, Intent(this, AdBlockerService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = Notification.Builder(this, "protection")
            .setSmallIcon(R.drawable.ic_shield).setContentTitle("Still protection is on")
            .setContentText("Filtering known ad domains on this device")
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(null, "Disconnect", stop).build()).build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
        else startForeground(1, notification)
    }

    private fun stopProtection(error: String? = null) {
        startup?.cancel()
        startup = null
        session?.close()
        session = null
        ProtectionStore.update { it.copy(connection = Connection.Disconnected, error = error) }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onRevoke() { stopProtection("VPN permission was revoked or another VPN was started."); super.onRevoke() }
    override fun onDestroy() {
        serviceScope.cancel()
        session?.close(); session = null
        ProtectionStore.update { it.copy(connection = Connection.Disconnected) }
        super.onDestroy()
    }

    private inner class Session(private val tunnel: ParcelFileDescriptor) {
        private val running = AtomicBoolean(true)
        private val writeLock = Any()
        @Volatile private var settings = AppSettings.load(this@AdBlockerService)
        private val historyLock = Any()
        private val resolver = UpstreamDnsResolver(this@AdBlockerService, AppSettings.load(this@AdBlockerService).dnsProvider.address)
        private val workers = ThreadPoolExecutor(4, 4, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue(128))
        private var reader: Thread? = null

        fun reloadSettings() = synchronized(historyLock) {
            settings = AppSettings.load(this@AdBlockerService)
            if (!settings.keepRecentDomains) ProtectionStore.update { it.copy(recentQueries = emptyList()) }
        }

        private fun record(query: PacketParser.Query, result: QueryResult, epoch: Long, millis: Long? = null) = synchronized(historyLock) {
            if (running.get()) ProtectionStore.record(query.question.name, result, settings.keepRecentDomains, millis, epoch)
        }

        fun start() {
            reader = thread(name = "still-tun-reader") {
                try {
                    val buffer = ByteArray(65535)
                    while (running.get()) {
                        // Nonblocking I/O keeps disconnect independent of incoming traffic.
                        val count = synchronized(writeLock) {
                            if (!running.get()) return@thread
                            try { Os.read(tunnel.fileDescriptor, buffer, 0, buffer.size) }
                            catch (error: ErrnoException) {
                                if (error.errno == OsConstants.EAGAIN) 0 else throw error
                            }
                        }
                        if (count < 0) break
                        if (count == 0) { Thread.sleep(20); continue }
                        val query = PacketParser.parse(buffer.copyOf(count)) ?: continue
                        val historyEpoch = ProtectionStore.historyEpoch
                        if (!query.destination.contentEquals(byteArrayOf(10, 0, 0, 2))) continue
                        ProtectionStore.update { it.copy(queries = it.queries + 1) }
                        val isBlocked = FilterPolicy.blocked(query.question.name, settings, FilterLibrary.get(this@AdBlockerService).state.value)
                        LifetimeStatistics.get(this@AdBlockerService).record(isBlocked)
                        if (isBlocked) {
                            write(query, PacketParser.response(query))
                            ProtectionStore.update { it.copy(blocked = it.blocked + 1) }
                            record(query, QueryResult.Blocked, historyEpoch)
                        } else {
                            try {
                                workers.execute {
                                    val started = android.os.SystemClock.elapsedRealtime()
                                    val response = try {
                                        resolver.resolve(query).also {
                                            record(query, QueryResult.Allowed, historyEpoch, android.os.SystemClock.elapsedRealtime() - started)
                                        }
                                    } catch (_: Exception) {
                                        if (running.get()) ProtectionStore.update { it.copy(failures = it.failures + 1) }
                                        record(query, QueryResult.Failed, historyEpoch)
                                        PacketParser.response(query, error = 2)
                                    }
                                    try { write(query, response) } catch (_: Exception) { failed() }
                                }
                            } catch (_: java.util.concurrent.RejectedExecutionException) {
                                ProtectionStore.update { it.copy(failures = it.failures + 1) }
                                record(query, QueryResult.Failed, historyEpoch)
                                write(query, PacketParser.response(query, error = 2))
                            }
                        }
                    }
                    if (running.get()) failed()
                } catch (_: Exception) { if (running.get()) failed() }
            }
        }

        private fun write(query: PacketParser.Query, dns: ByteArray) = synchronized(writeLock) {
            if (running.get()) {
                val packet = PacketParser.wrap(query, dns)
                Os.write(tunnel.fileDescriptor, packet, 0, packet.size)
            }
        }

        private fun failed() {
            android.os.Handler(mainLooper).post {
                if (session === this && running.get()) stopProtection("The DNS tunnel stopped. Tap to reconnect.")
            }
        }

        fun close() {
            if (!running.getAndSet(false)) return
            resolver.close()
            workers.shutdownNow()
            // Wake the idle reader; the descriptor has a single owner and cannot block close.
            reader?.interrupt()
            synchronized(writeLock) {
                runCatching { tunnel.close() }
            }
        }
    }
}
