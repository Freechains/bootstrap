package org.freechains.bootstrap

import org.freechains.common.*

val help = """
freechains-bootstrap $VERSION

Usage:
    freechains-bootstrap <chain>

Options:
    --help          displays this help
    --version       displays software version
    --port          port to connect [default: $PORT_8330]

More Information:

    http://www.freechains.org/

    Please report bugs at <http://github.com/Freechains/README/>.
"""

fun main (args: Array<String>) {
    main_ { main_bootstrap(args) }
}

fun main_bootstrap_assert (args: Array<String>) : String {
    return main_assert_ { main_bootstrap(args) }
}

fun main_bootstrap (args: Array<String>) : Pair<Boolean,String> {
    return main_catch_("freechains-bootstrap", VERSION, help, args) { cmds, opts ->
        val port = opts["--port"]?.toInt() ?: PORT_8330

        assert_(cmds.size == 1) { "invalid number of arguments" }
        Chain(cmds[0], port)
        while (true);
        @Suppress("UNREACHABLE_CODE")
        Pair(true, "")
    }
}
