package org.freechains.bootstrap

import org.freechains.common.*
import org.freechains.cli.main_cli_assert
import kotlinx.serialization.Serializable
import kotlinx.serialization.UnstableDefault
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.net.Socket
import kotlin.concurrent.thread

@Serializable
data class Store (
    val peers  : MutableList<String>,
    val chains : MutableList<Pair<String,HKey?>>
)

class Bootstrap (path: String, chain: String, port: Int = PORT_8330) {
    val cbs: MutableSet<(Store)->Unit> = mutableSetOf()

    private val chain = chain
    private var data  = Store(mutableListOf(), mutableListOf())

    private fun json2data (json: String) {
        @OptIn(UnstableDefault::class)
        this.data = Json(JsonConfiguration(prettyPrint=true)).parse(Store.serializer(), json)
    }
    private fun data2json (): String {
        @OptIn(UnstableDefault::class)
        return Json(JsonConfiguration(prettyPrint=true)).stringify(Store.serializer(), this.data)
    }

    init {
        val file = File(path)
        if (!file.exists()) {
            File(path).writeText(this.data2json())
        } else {
            this.json2data(file.readText())
        }
        this.update()

        // background listen
        thread {
            val socket = Socket("localhost", port)
            val writer = DataOutputStream(socket.getOutputStream()!!)
            val reader = DataInputStream(socket.getInputStream()!!)
            writer.writeLineX("$PRE chain $chain listen")
            while (true) {
                reader.readLineX()
                this.update()
            }
        }
    }

    // update data and invoke callbacks
    fun update () {
        thread {
            val head = main_cli_assert(arrayOf("chain", chain, "heads", "all")).let {
                val heads = it.split(' ')
                assert_(heads.size == 1)
                heads[0]
            }
            val pay = main_cli_assert(arrayOf("chain", chain, "get", "payload", head))
            synchronized (this) {
                this.json2data(pay)
                this.cbs.forEach { it(this.data) }
            }
        }
    }

    @Synchronized
    fun write (f: (Store)->Unit) {
        f(this.data)
        val json = this.data2json()
        thread {
            main_cli_assert(arrayOf("chain", this.chain, "post", "inline", json))
        }
    }

    @Synchronized
    fun <T> read (f: (Store)->T) : T {
        return f(this.data)
    }
}