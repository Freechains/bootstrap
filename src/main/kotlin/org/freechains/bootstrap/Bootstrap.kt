package org.freechains.bootstrap

import org.freechains.cli.main_cli
import org.freechains.cli.main_cli_assert
import org.freechains.common.HKey
import org.freechains.common.PORT_8330
import org.freechains.common.listSplit
import java.io.File

class Bootstrap (root: String, port: Int = PORT_8330) {
    val chains: MutableSet<Chain>

    private val root = root
    private val port = port
    private val host = "--host=localhost:$port"

    init {
        this.chains = File(root)
            .list()!!
            .filter { f -> f.endsWith(".bootstrap") }
            .map    { f -> f.dropLast(".bootstrap".length) }
            .map    { Chain(root, it, port) }
            .toMutableSet()
    }

    fun boot (peer: String, chain: String, key: HKey) {
        assert(chain.startsWith("\$bootstrap."))
        main_cli(arrayOf(host, "chains", "join", chain, key))
        this.chains.add(Chain(root, chain, this.port))
        main_cli_assert(arrayOf(host, "peer", peer, "recv", chain))
    }

    fun query (peer: String): List<String> {
        return main_cli_assert(arrayOf(host, "peer", peer, "list"))
            .listSplit()
            .filter { it.startsWith("\$bootstrap.") }
    }
}