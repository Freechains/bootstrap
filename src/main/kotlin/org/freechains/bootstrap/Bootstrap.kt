package org.freechains.bootstrap

import org.freechains.cli.main_cli
import org.freechains.cli.main_cli_assert
import org.freechains.common.HKey
import org.freechains.common.PORT_8330
import org.freechains.common.listSplit
import java.io.File

class Bootstrap (root: String, host: String = "localhost:$PORT_8330") {
    val chains: MutableSet<Chain>

    private val root  = root
    private val host  = "--host=$host"
    private val hhost = "--host=$host"

    init {
        this.chains = File(root)
            .listFiles { f, _ -> f.extension == ".bootstrap" }!!
            .map { Chain(root, it.nameWithoutExtension, host) }
            .toMutableSet()
    }

    fun boot (peer: String, chain: String, key: HKey) {
        assert(chain.startsWith("\$bootstrap."))
        main_cli(arrayOf(hhost, "chains", "join", chain, key))
        this.chains.add(Chain(root, chain, host))
        main_cli_assert(arrayOf(hhost, "peer", peer, "recv", chain))
    }

    fun query (peer: String): List<String> {
        return main_cli_assert(arrayOf(hhost, "peer", peer, "list"))
            .listSplit()
            .filter { it.startsWith("\$bootstrap.") }
    }
}