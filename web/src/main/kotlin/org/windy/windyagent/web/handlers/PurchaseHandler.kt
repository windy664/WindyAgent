package org.windy.windyagent.web.handlers

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.sun.net.httpserver.HttpExchange
import org.windy.windyagent.web.ApiHandler
import org.windy.windyagent.web.DashboardServer

/**
 * 充值管理 API — 通过 PurchaseBridge 桥接 WindyPurchase。
 *
 * 本类不引用任何 WindyPurchase 类型（PurchaseApi / Order / Product），
 * 所有跨 classloader 交互封装在 PurchaseBridge 中。
 */
class PurchaseHandler(
    private val server: DashboardServer,
    private val bridge: PurchaseBridge
) : ApiHandler {

    override fun canHandle(path: String): Boolean = path.startsWith("/api/purchase/")

    override fun handle(ex: HttpExchange, path: String, query: Map<String, String>, mapper: ObjectMapper) {
        val sub = path.removePrefix("/api/purchase").trimStart('/')

        if (sub == "available") {
            json(ex, 200, """{"available":${bridge.isAvailable()}}"""); return
        }
        if (!bridge.isAvailable()) { json(ex, 503, """{"error":"WindyPurchase not installed"}"""); return }

        when {
            sub == "orders" && ex.requestMethod == "GET" -> {
                val page = query["page"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
                val size = query["size"]?.toIntOrNull()?.coerceIn(1, 100) ?: 20
                val result = bridge.getOrders(page, size, query["status"], query["method"], query["player"])
                json(ex, 200, result.toString())
            }
            sub.startsWith("orders/") && sub.endsWith("/close") && ex.requestMethod == "POST" -> {
                val id = sub.removePrefix("orders/").removeSuffix("/close")
                json(ex, if (bridge.cancelOrder(id)) 200 else 400, """{"ok":true}""")
            }
            sub.startsWith("orders/") && sub.endsWith("/refund") && ex.requestMethod == "POST" -> {
                val id = sub.removePrefix("orders/").removeSuffix("/refund")
                json(ex, if (bridge.refundOrder(id)) 200 else 400, """{"ok":true}""")
            }
            sub.startsWith("orders/") && ex.requestMethod == "GET" -> {
                val order = bridge.getOrder(sub.removePrefix("orders/"))
                if (order != null) json(ex, 200, order.toString())
                else json(ex, 404, """{"error":"not found"}""")
            }
            sub == "products" && ex.requestMethod == "GET" -> {
                json(ex, 200, bridge.getProducts().toString())
            }
            sub == "products" && ex.requestMethod == "POST" -> {
                val b = mapper.readTree(server.body(ex))
                val id = b["id"]?.asText() ?: return json(ex, 400, """{"error":"id required"}""")
                val price = b["price"]?.asInt()?.takeIf { it > 0 } ?: return json(ex, 400, """{"error":"price required"}""")
                bridge.saveProduct(id, b["name"]?.asText() ?: id, price, b["description"]?.asText() ?: "",
                    b["commands"]?.map { it.asText() } ?: emptyList(), b["permission"]?.asText(), b["cooldown"]?.asInt() ?: 0)
                json(ex, 200, """{"ok":true}""")
            }
            sub.startsWith("products/") && ex.requestMethod == "DELETE" -> {
                json(ex, if (bridge.deleteProduct(sub.removePrefix("products/"))) 200 else 404, """{"ok":true}""")
            }
            sub == "revenue" && ex.requestMethod == "GET" -> {
                json(ex, 200, bridge.getRevenue().toString())
            }
            sub == "revenue/ranking" && ex.requestMethod == "GET" -> {
                json(ex, 200, bridge.getRevenueRanking(query["limit"]?.toIntOrNull() ?: 10).toString())
            }
            sub == "grant" && ex.requestMethod == "POST" -> {
                val b = mapper.readTree(server.body(ex))
                val player = b["player"]?.asText() ?: return json(ex, 400, """{"error":"player required"}""")
                val amount = b["amount"]?.asInt()?.takeIf { it > 0 } ?: return json(ex, 400, """{"error":"amount required"}""")
                val cmds = b["commands"]?.map { it.asText() } ?: emptyList()
                val result = bridge.grantOrder(player, amount, cmds)
                if (result != null) json(ex, 200, result.toString())
                else json(ex, 500, """{"error":"grant failed"}""")
            }
            else -> json(ex, 404, """{"error":"not found"}""")
        }
    }

    private fun json(ex: HttpExchange, code: Int, body: String) = server.json(ex, code, body)
}
