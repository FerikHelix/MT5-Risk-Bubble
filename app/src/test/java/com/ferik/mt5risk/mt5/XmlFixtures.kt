package com.ferik.mt5risk.mt5

import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/** [UiNode] over a `uiautomator dump` element, so MT5 screens recorded on the device run on the JVM. */
class XmlUiNode(private val element: Element) : UiNode {

    override val packageName: String? get() = attr("package")
    override val viewId: String? get() = attr("resource-id")
    override val text: String? get() = element.getAttribute("text")
    override val className: String? get() = attr("class")
    override val isClickable: Boolean get() = element.getAttribute("clickable") == "true"
    override val isEditable: Boolean get() = className?.endsWith("EditText") == true

    /** uiautomator only dumps visible nodes; a test can set `visible-to-user="false"` to model an off-screen one. */
    override val isVisible: Boolean get() = element.getAttribute("visible-to-user") != "false"
    override val bounds: Bounds get() = parseBounds(element.getAttribute("bounds"))

    override val children: List<UiNode> by lazy {
        val nodes = element.childNodes
        (0 until nodes.length).mapNotNull { index ->
            (nodes.item(index) as? Element)?.takeIf { it.tagName == "node" }?.let(::XmlUiNode)
        }
    }

    private fun attr(name: String): String? = element.getAttribute(name).ifEmpty { null }

    private fun parseBounds(raw: String): Bounds {
        // "[440,1299][715,1420]"
        val numbers = Regex("-?\\d+").findAll(raw).map { it.value.toInt() }.toList()
        return if (numbers.size == 4) Bounds(numbers[0], numbers[1], numbers[2], numbers[3]) else Bounds(0, 0, 0, 0)
    }
}

/** Screens recorded from the demo-account phone, stored under `src/test/resources/mt5/`. */
object Fixtures {

    val ALL = listOf(
        "collapsed_strip",
        "collapsed_hidden_ticket", // collapsed_strip plus the hidden ticket nodes, values taken from the live probe
        "collapsed_hidden_no_stops",
        "chart_no_ticket",
        "expanded_market_no_stops",
        "expanded_market_sl_set",
        "expanded_market_sl_typed",
        "expanded_pending_no_stops",
        "expanded_pending_stops_zero",
        "trade_tab",
    )

    fun text(name: String): String {
        val stream = Fixtures::class.java.getResourceAsStream("/mt5/$name.xml")
            ?: error("missing fixture mt5/$name.xml")
        return stream.use { String(it.readBytes(), Charsets.UTF_8) }
    }

    /** Load a fixture, optionally editing the raw XML first to build a variant of a recorded screen. */
    fun load(name: String, edit: (String) -> String = { it }): UiNode = parse(edit(text(name)))

    fun parse(xml: String): UiNode {
        val factory = DocumentBuilderFactory.newInstance()
        val document = factory.newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
        return XmlUiNode(document.documentElement)
    }
}
