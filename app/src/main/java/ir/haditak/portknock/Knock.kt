package ir.haditak.portknock

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

enum class Proto { TCP, UDP, ICMP }

data class Knock(val proto: Proto = Proto.TCP, val port: String = "")

data class Saved(
    val host: String,
    val verify: String,
    val delay: String,
    val knocks: List<Knock>
)

class KnockResult(val ok: Boolean, val message: String)

object Store {
    private const val PREFS = "knock_prefs"

    fun load(ctx: Context): Saved {
        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val list = mutableListOf<Knock>()
        try {
            val arr = JSONArray(sp.getString("knocks", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(Knock(Proto.valueOf(o.getString("p")), o.getString("port")))
            }
        } catch (_: Exception) {
        }
        return Saved(
            host = sp.getString("host", "") ?: "",
            verify = sp.getString("verify", "") ?: "",
            delay = sp.getString("delay", "300") ?: "300",
            knocks = list
        )
    }

    fun save(ctx: Context, host: String, verify: String, delay: String, knocks: List<Knock>) {
        val arr = JSONArray()
        knocks.forEach { arr.put(JSONObject().put("p", it.proto.name).put("port", it.port)) }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("host", host)
            .putString("verify", verify)
            .putString("delay", delay)
            .putString("knocks", arr.toString())
            .apply()
    }
}

object KnockEngine {

    /** Runs the whole sequence, then checks whether the door really opened. */
    suspend fun run(
        host: String,
        knocks: List<Knock>,
        delayMs: Long,
        verifyPort: String,
        onStep: (Int) -> Unit
    ): KnockResult {
        if (host.isBlank()) return KnockResult(false, "آدرس مقصد خالی است")

        val verify = verifyPort.trim()
        val verifyNum = verify.toIntOrNull()
        if (verify.isNotEmpty() && (verifyNum == null || verifyNum !in 1..65535)) {
            return KnockResult(false, "پورت تست معتبر نیست")
        }
        knocks.forEachIndexed { i, k ->
            if (k.proto != Proto.ICMP) {
                val p = k.port.toIntOrNull()
                if (p == null || p !in 1..65535) {
                    return KnockResult(false, "پورت ناک ${i + 1} معتبر نیست")
                }
            }
        }

        val addr: InetAddress = try {
            resolve(host.trim())
        } catch (e: Exception) {
            return KnockResult(false, "آدرس پیدا نشد (اینترنت یا DNS را بررسی کن)")
        }

        for ((i, k) in knocks.withIndex()) {
            onStep(i)
            send(addr, k)
            delay(delayMs)
        }

        onStep(knocks.size) // checking
        delay(500)

        return if (verifyNum != null) {
            if (tcpOpen(addr, verifyNum)) {
                KnockResult(true, "ارتباط برقرار شد: پورت $verifyNum باز است")
            } else {
                KnockResult(false, "ارتباط برقرار نشد: پورت $verifyNum جواب نداد")
            }
        } else {
            if (pingOk(addr)) {
                KnockResult(true, "ارتباط برقرار شد: روتر به پینگ جواب داد")
            } else {
                KnockResult(false, "ارتباط برقرار نشد: روتر به پینگ جواب نداد")
            }
        }
    }

    private suspend fun resolve(host: String): InetAddress = withContext(Dispatchers.IO) {
        val all = InetAddress.getAllByName(host)
        all.firstOrNull { it is Inet4Address } ?: all.first()
    }

    private suspend fun send(addr: InetAddress, k: Knock) = withContext(Dispatchers.IO) {
        val port = k.port.toIntOrNull() ?: 0
        try {
            when (k.proto) {
                Proto.TCP -> Socket().use { it.connect(InetSocketAddress(addr, port), 400) }
                Proto.UDP -> DatagramSocket().use {
                    it.send(DatagramPacket(byteArrayOf(0), 1, addr, port))
                }
                Proto.ICMP -> ping(addr)
            }
        } catch (_: Exception) {
            // Expected: the knock port is closed/dropped. The packet was still sent.
        }
    }

    private suspend fun tcpOpen(addr: InetAddress, port: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            Socket().use { it.connect(InetSocketAddress(addr, port), 3000) }
            true
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun pingOk(addr: InetAddress): Boolean = withContext(Dispatchers.IO) {
        ping(addr) || ping(addr)
    }

    // Android apps cannot open raw ICMP sockets, so the system ping binary is used.
    private fun ping(addr: InetAddress): Boolean {
        return try {
            val p = ProcessBuilder("/system/bin/ping", "-c", "1", "-W", "1", addr.hostAddress)
                .redirectErrorStream(true)
                .start()
            val done = p.waitFor(4, TimeUnit.SECONDS)
            if (!done) {
                p.destroy()
                false
            } else {
                p.exitValue() == 0
            }
        } catch (_: Exception) {
            false
        }
    }
}
