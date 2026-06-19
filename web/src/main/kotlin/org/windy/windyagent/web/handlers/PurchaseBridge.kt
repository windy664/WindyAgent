package org.windy.windyagent.web.handlers

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import org.slf4j.LoggerFactory

/**
 * WindyPurchase 跨 classloader 桥接。
 *
 * 所有 WindyPurchase 类型的反射交互封装在此，对外只暴露 Jackson JsonNode。
 * PurchaseHandler 不接触任何 WindyPurchase 类型。
 */
class PurchaseBridge(private val windyPurchaseLoader: ClassLoader?) {

    private val log = LoggerFactory.getLogger("WindyAgent-Purchase")
    private val mapper = ObjectMapper()
    private var resolved = false
    private var api: Any? = null
    private var apiClass: Class<*>? = null

    fun isAvailable(): Boolean {
        resolve()
        return api != null
    }

    // ==================== 订单 ====================

    fun getOrders(page: Int, size: Int, status: String?, method: String?, player: String?): JsonNode {
        val api = api ?: return emptyArr()
        val orders = call(api, "getOrders", page, size, status, method, player) as? List<*> ?: return emptyArr()
        val total = call(api, "countOrders", status, method, player) as? Int ?: 0
        return obj {
            put("total", total)
            put("page", page)
            put("size", size)
            set<ArrayNode>("data", arr(orders.mapNotNull { orderJson(it) }))
        }
    }

    fun getOrder(id: String): JsonNode? {
        val order = call(api ?: return null, "getOrder", id) ?: return null
        return orderJson(order)
    }

    fun cancelOrder(id: String): Boolean {
        return call(api ?: return false, "cancelOrder", id) as? Boolean ?: false
    }

    fun refundOrder(id: String): Boolean {
        return call(api ?: return false, "refundOrder", id) as? Boolean ?: false
    }

    // ==================== 商品 ====================

    fun getProducts(): JsonNode {
        val api = api ?: return emptyArr()
        val products = call(api, "getProducts") as? List<*> ?: return emptyArr()
        return arr(products.mapNotNull { productJson(it) })
    }

    fun saveProduct(id: String, name: String, price: Int, desc: String, cmds: List<String>, perm: String?, cd: Int) {
        val api = api ?: return
        val loader = apiClass?.classLoader ?: return
        val productClass = loader.loadClass("org.windy.purchase.core.model.Product")
        val product = productClass.constructors.first { it.parameterCount == 7 }.newInstance(id, name, price, desc, cmds, perm, cd)
        call(api, "saveProduct", product)
    }

    fun deleteProduct(id: String): Boolean {
        return call(api ?: return false, "deleteProduct", id) as? Boolean ?: false
    }

    // ==================== 统计 ====================

    fun getRevenue(): JsonNode {
        val r = call(api ?: return obj {}, "getRevenue") ?: return obj {}
        return obj {
            put("totalAmount", intField(r, "totalAmountFen"))
            put("todayAmount", intField(r, "todayAmountFen"))
            put("totalOrders", intField(r, "totalOrders"))
        }
    }

    fun getRevenueRanking(limit: Int): JsonNode {
        val api = api ?: return emptyArr()
        val ranking = call(api, "getRevenueRanking", limit) as? List<*> ?: return emptyArr()
        return arr(ranking.mapNotNull { r ->
            if (r == null) null
            else obj {
                put("playerId", strField(r, "playerId"))
                put("playerName", strField(r, "playerName"))
                put("totalAmount", intField(r, "totalAmountFen"))
            }
        })
    }

    // ==================== 补单 ====================

    fun grantOrder(player: String, amount: Int, cmds: List<String>): JsonNode? {
        val order = call(api ?: return null, "grantOrder", player, amount, cmds) ?: return null
        return obj {
            put("orderId", strField(order, "orderId"))
            put("player", strField(order, "playerName"))
            put("amount", intField(order, "amount"))
        }
    }

    // ==================== 内部：classloader 桥接 ====================

    private fun resolve() {
        if (resolved) return
        resolved = true
        val loader = windyPurchaseLoader
        if (loader == null) {
            log.warn("WindyPurchase classloader is null — plugin not found or not loaded yet")
            return
        }
        log.info("WindyPurchase classloader: {}", loader)
        try {
            val provider = Class.forName("org.windy.purchase.core.api.PurchaseApiProvider", true, loader)
            log.info("Loaded PurchaseApiProvider: {}, classloader: {}", provider, provider.classLoader)

            // Kotlin object: 读 INSTANCE 字段确认单例存在
            val objInstance = try { provider.getField("INSTANCE").get(null) } catch (_: Exception) { null }
            log.info("PurchaseApiProvider.INSTANCE: {}", objInstance)

            // 读 volatile instance 字段
            val field = try { provider.getDeclaredField("instance").apply { isAccessible = true } } catch (_: Exception) { null }
            val fieldValue = field?.get(null)
            log.info("PurchaseApiProvider.instance field: {}", fieldValue)

            // get() 通过 Kotlin object INSTANCE 委托时可能返回 null，直接用字段值
            val instance = fieldValue
            if (instance == null) {
                log.warn("PurchaseApiProvider.instance is null — register() not called")
            } else {
                api = instance
                apiClass = instance.javaClass
                log.info("PurchaseApi resolved: {}", apiClass?.name)
            }
        } catch (e: Exception) {
            log.error("Failed to resolve PurchaseApi: {}", e.message, e)
        }
    }

    private fun call(obj: Any, method: String, vararg args: Any?): Any? {
        val cls = obj.javaClass
        val m = cls.methods.firstOrNull { it.name == method && it.parameterCount == args.size } ?: return null
        return m.invoke(obj, *args)
    }

    // ==================== 字段读取（强类型） ====================

    private fun strField(obj: Any, name: String): String {
        return try { (obj.javaClass.getField(name).get(obj) ?: "").toString() }
        catch (_: Exception) {
            try { (obj.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(obj) ?: "").toString() }
            catch (_: Exception) { "" }
        }
    }

    private fun intField(obj: Any, name: String): Int {
        return try { (obj.javaClass.getField(name).get(obj) as? Number)?.toInt() ?: 0 }
        catch (_: Exception) {
            try { (obj.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(obj) as? Number)?.toInt() ?: 0 }
            catch (_: Exception) { 0 }
        }
    }

    private fun longField(obj: Any, name: String): Long {
        return try { (obj.javaClass.getField(name).get(obj) as? Number)?.toLong() ?: 0L }
        catch (_: Exception) {
            try { (obj.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(obj) as? Number)?.toLong() ?: 0L }
            catch (_: Exception) { 0L }
        }
    }

    private fun nullableStrField(obj: Any, name: String): String? {
        val v = try { obj.javaClass.getField(name).get(obj) }
        catch (_: Exception) {
            try { obj.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(obj) }
            catch (_: Exception) { null }
        }
        return v?.toString()
    }

    // ==================== JSON 序列化 ====================

    private fun orderJson(o: Any?): ObjectNode? {
        if (o == null) return null
        return obj {
            put("orderId", strField(o, "orderId"))
            put("playerId", strField(o, "playerId"))
            put("playerName", strField(o, "playerName"))
            nullableStrField(o, "productId")?.let { put("productId", it) }
            put("amount", intField(o, "amount"))
            put("currency", strField(o, "currency").ifEmpty { "CNY" })
            put("paymentMethod", strField(o, "paymentMethod"))
            put("status", run {
                val s = try { o.javaClass.getField("status").get(o) } catch (_: Exception) { null }
                if (s == null) "UNKNOWN"
                else try { s.javaClass.getMethod("name").invoke(s) as String } catch (_: Exception) { s.toString() }
            })
            nullableStrField(o, "qrCodeUrl")?.let { put("qrCodeUrl", it) }
            nullableStrField(o, "rawUrl")?.let { put("rawUrl", it) }
            put("createdAt", longField(o, "createdAt"))
            val paidAt = longField(o, "paidAt")
            if (paidAt > 0) put("paidAt", paidAt)
            @Suppress("UNCHECKED_CAST")
            val cmds = try { o.javaClass.getField("commands").get(o) as? List<String> } catch (_: Exception) { null }
            set<ArrayNode>("commands", arr((cmds ?: emptyList()).map { mapper.valueToTree<JsonNode>(it) }))
        }
    }

    private fun productJson(p: Any?): ObjectNode? {
        if (p == null) return null
        return obj {
            put("id", strField(p, "id"))
            put("name", strField(p, "name"))
            put("price", intField(p, "price"))
            put("description", strField(p, "description"))
            nullableStrField(p, "permission")?.let { put("permission", it) }
            put("cooldown", intField(p, "cooldown"))
            @Suppress("UNCHECKED_CAST")
            val cmds = try { p.javaClass.getField("commands").get(p) as? List<String> } catch (_: Exception) { null }
            set<ArrayNode>("commands", arr((cmds ?: emptyList()).map { mapper.valueToTree<JsonNode>(it) }))
        }
    }

    // ==================== Jackson 工具 ====================

    private fun obj(block: ObjectNode.() -> Unit): ObjectNode = mapper.createObjectNode().apply(block)
    private fun arr(items: List<JsonNode>): ArrayNode = mapper.createArrayNode().apply { items.forEach { add(it) } }
    private fun emptyArr(): ArrayNode = mapper.createArrayNode()
}
