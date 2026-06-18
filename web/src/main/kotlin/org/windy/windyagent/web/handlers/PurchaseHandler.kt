package org.windy.windyagent.web.handlers

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.sun.net.httpserver.HttpExchange
import org.windy.windyagent.web.ApiHandler
import org.windy.windyagent.web.DashboardServer
import org.windy.purchase.core.api.PurchaseApiProvider

/**
 * 充值管理 API — 桥接 WindyPurchase 的 PurchaseApi。
 *
 * WindyPurchase 未安装时所有接口返回 503，前端据此隐藏功能。
 */
class PurchaseHandler(private val server: DashboardServer) : ApiHandler {

    override fun canHandle(path: String): Boolean = path.startsWith("/api/purchase/")

    override fun handle(ex: HttpExchange, path: String, query: Map<String, String>, mapper: ObjectMapper) {
        val api = PurchaseApiProvider.get()
        val sub = path.removePrefix("/api/purchase")

        // 未安装检测
        if (sub == "available") {
            json(ex, 200, """{"available":${api != null}}"""); return
        }
        if (api == null) { json(ex, 503, """{"error":"WindyPurchase not installed"}"""); return }

        when {
            // 订单
            sub == "orders" && ex.requestMethod == "GET" -> {
                val page = query["page"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
                val size = query["size"]?.toIntOrNull()?.coerceIn(1, 100) ?: 20
                val orders = api.getOrders(page, size, query["status"], query["method"], query["player"])
                val total = api.countOrders(query["status"], query["method"], query["player"])
                json(ex, 200, mapper.createObjectNode().apply {
                    put("total", total); put("page", page); put("size", size)
                    set<ArrayNode>("data", mapper.createArrayNode().apply { orders.forEach { add(orderJson(it, mapper)) } })
                }.toString())
            }
            sub.startsWith("orders/") && sub.endsWith("/close") && ex.requestMethod == "POST" -> {
                val id = sub.removePrefix("orders/").removeSuffix("/close")
                val ok = api.cancelOrder(id)
                json(ex, if (ok) 200 else 400, """{"ok":$ok}""")
            }
            sub.startsWith("orders/") && sub.endsWith("/refund") && ex.requestMethod == "POST" -> {
                val id = sub.removePrefix("orders/").removeSuffix("/refund")
                val ok = api.refundOrder(id)
                json(ex, if (ok) 200 else 400, """{"ok":$ok}""")
            }
            sub.startsWith("orders/") && ex.requestMethod == "GET" -> {
                val id = sub.removePrefix("orders/")
                val order = api.getOrder(id)
                if (order != null) json(ex, 200, orderJson(order, mapper).toString())
                else json(ex, 404, """{"error":"not found"}""")
            }
            // 商品
            sub == "products" && ex.requestMethod == "GET" -> {
                json(ex, 200, mapper.writeValueAsString(api.getProducts().map { productJson(it, mapper) }))
            }
            sub == "products" && ex.requestMethod == "POST" -> {
                val b = mapper.readTree(server.body(ex))
                val id = b["id"]?.asText() ?: return json(ex, 400, """{"error":"id required"}""")
                val price = b["price"]?.asInt()?.takeIf { it > 0 } ?: return json(ex, 400, """{"error":"price required"}""")
                api.saveProduct(org.windy.purchase.core.model.Product(
                    id = id, name = b["name"]?.asText() ?: id, price = price,
                    description = b["description"]?.asText() ?: "",
                    commands = b["commands"]?.map { it.asText() } ?: emptyList(),
                    permission = b["permission"]?.asText(), cooldown = b["cooldown"]?.asInt() ?: 0
                ))
                json(ex, 200, """{"ok":true}""")
            }
            sub.startsWith("products/") && ex.requestMethod == "DELETE" -> {
                val id = sub.removePrefix("products/")
                json(ex, if (api.deleteProduct(id)) 200 else 404, """{"ok":true}""")
            }
            // 统计
            sub == "revenue" && ex.requestMethod == "GET" -> {
                val r = api.getRevenue()
                json(ex, 200, """{"totalAmount":${r.totalAmountFen},"todayAmount":${r.todayAmountFen},"totalOrders":${r.totalOrders}}""")
            }
            sub == "revenue/ranking" && ex.requestMethod == "GET" -> {
                val limit = query["limit"]?.toIntOrNull() ?: 10
                json(ex, 200, mapper.writeValueAsString(api.getRevenueRanking(limit).map { r ->
                    mapper.createObjectNode().put("playerId", r.playerId).put("playerName", r.playerName).put("totalAmount", r.totalAmountFen)
                }))
            }
            // 补单
            sub == "grant" && ex.requestMethod == "POST" -> {
                val b = mapper.readTree(server.body(ex))
                val player = b["player"]?.asText() ?: return json(ex, 400, """{"error":"player required"}""")
                val amount = b["amount"]?.asInt()?.takeIf { it > 0 } ?: return json(ex, 400, """{"error":"amount required"}""")
                val cmds = b["commands"]?.map { it.asText() } ?: emptyList()
                val order = api.grantOrder(player, amount, cmds)
                if (order != null) json(ex, 200, """{"orderId":"${order.orderId}","player":"${order.playerName}","amount":${order.amount}}""")
                else json(ex, 500, """{"error":"grant failed"}""")
            }
            else -> json(ex, 404, """{"error":"not found"}""")
        }
    }

    private fun json(ex: HttpExchange, code: Int, body: String) = server.json(ex, code, body)

    private fun orderJson(o: org.windy.purchase.core.model.Order, m: ObjectMapper): ObjectNode = m.createObjectNode().apply {
        put("orderId", o.orderId); put("playerId", o.playerId); put("playerName", o.playerName)
        put("productId", o.productId); put("amount", o.amount); put("currency", o.currency)
        put("paymentMethod", o.paymentMethod); put("status", o.status.name)
        put("qrCodeUrl", o.qrCodeUrl); put("rawUrl", o.rawUrl)
        put("createdAt", o.createdAt); put("paidAt", o.paidAt)
        set<ArrayNode>("commands", m.createArrayNode().apply { o.commands.forEach { add(it) } })
    }

    private fun productJson(p: org.windy.purchase.core.model.Product, m: ObjectMapper): ObjectNode = m.createObjectNode().apply {
        put("id", p.id); put("name", p.name); put("price", p.price)
        put("description", p.description); put("permission", p.permission); put("cooldown", p.cooldown)
        set<ArrayNode>("commands", m.createArrayNode().apply { p.commands.forEach { add(it) } })
    }
}
