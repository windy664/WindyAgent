// name: weather
// description: 查询指定城市的实时天气(联网调用外部接口 wttr.in)。玩家或腐竹问某地天气/出门要不要带伞/冷不冷时用
// arg: city string 城市名,中文/拼音/英文均可,如 北京/Shanghai/London
// —— 这是「纯脚本」技能,演示如何在技能里调用第三方 HTTP API。
// 注入变量:server(Server) / plugins(PluginManager) / actions / args(Map) / log
// ⚠️ 技能在主线程执行,这里做了网络请求并把超时压到 3~4 秒;若外网慢会短暂卡服,生产可换异步缓存方案。
import groovy.json.JsonSlurper

def city = (args?.city ?: "北京").toString().trim()
def q = URLEncoder.encode(city, "UTF-8")
def url = "https://wttr.in/${q}?format=j1&lang=zh-cn"
try {
    def conn = (HttpURLConnection) new URL(url).openConnection()
    // wttr.in 对浏览器 UA 返回网页、对 curl UA 返回数据,这里伪装成 curl 拿 JSON
    conn.setRequestProperty("User-Agent", "curl/8.5.0")
    conn.setRequestProperty("Accept-Language", "zh-CN")
    conn.connectTimeout = 3000
    conn.readTimeout = 4000
    if (conn.responseCode != 200) return "查询「${city}」天气失败:HTTP ${conn.responseCode}"
    def data = conn.inputStream.withCloseable { new JsonSlurper().parse(it, "UTF-8") }
    def cur = data.current_condition?.getAt(0)
    if (cur == null) return "未查询到「${city}」的天气数据"
    // 中文天气描述优先 lang_zh,缺失回退英文 weatherDesc
    def desc = (cur.lang_zh ?: cur.weatherDesc)?.getAt(0)?.value ?: "未知"
    def area = data.nearest_area?.getAt(0)?.areaName?.getAt(0)?.value ?: city
    return "${area} 实时天气:${desc},气温 ${cur.temp_C}°C(体感 ${cur.FeelsLikeC}°C)," +
           "湿度 ${cur.humidity}%,风速 ${cur.windspeedKmph} km/h"
} catch (Exception e) {
    return "查询「${city}」天气失败:${e.class.simpleName} ${e.message}"
}
