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
data class Chain (
    val name : String,
    val key  : HKey?
)

@Serializable
data class Id (
    val nick : String,
    val pub  : HKey
)

@Serializable
data class DBootstrap (
    val peers  : MutableList<String>,
    val chains : MutableList<Chain>,
    val ids    : MutableList<Id>,
    val cts    : MutableList<Id>
)

class Bootstrap (root: String, chain: String, port: Int) {
    val cbs: MutableSet<(DBootstrap)->Unit> = mutableSetOf()

    private val chain = chain
    private val path  = root + "/" + chain + "/bootstrap.json"
    private var data = DBootstrap(mutableListOf(), mutableListOf(), mutableListOf(), mutableListOf())

    private fun json2data (json: String) {
        @OptIn(UnstableDefault::class)
        this.data = Json(JsonConfiguration(prettyPrint=true)).parse(DBootstrap.serializer(), json)
    }
    private fun data2json (): String {
        @OptIn(UnstableDefault::class)
        return Json(JsonConfiguration(prettyPrint=true)).stringify(DBootstrap.serializer(), this.data)
    }

    init {
        val file = File(this.path)
        if (!file.exists()) {
            File(this.path).writeText(this.data2json())
        } else {
            this.json2data(file.readText())
        }

        // background listen
        thread {
            val socket = Socket("localhost", port)
            val writer = DataOutputStream(socket.getOutputStream()!!)
            val reader = DataInputStream(socket.getInputStream()!!)
            writer.writeLineX("$PRE chain $chain listen")
            while (true) {
                reader.readLineX()
                val head = main_cli_assert(arrayOf("chain", this.chain, "heads", "all")).let {
                    val heads = it.split(' ')
                    assert_(heads.size == 1)
                    heads[0]
                }
                val pay = main_cli_assert(arrayOf("chain", this.chain, "get", "payload", head))
                synchronized (this) {
                    this.json2data(pay)
                    this.cbs.forEach { it(this.data) }
                }
            }
        }
    }

    @Synchronized
    fun write (f: (DBootstrap)->Unit) {
        f(this.data)
        val json = this.data2json()
        thread {
            main_cli_assert(arrayOf("chain", this.chain, "post", "inline", json))
        }
    }

    @Synchronized
    fun <T> read (f: (DBootstrap)->T) : T {
        return f(this.data)
    }
}