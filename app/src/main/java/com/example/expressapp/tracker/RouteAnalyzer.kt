package com.example.expressapp.tracker

interface RouteAnalyzer {
    fun deriveFrom(traces: List<TraceItem>): String
    fun deriveTo(traces: List<TraceItem>): String
    fun statusTag(traces: List<TraceItem>): String
}

class ProvinceRouteAnalyzer : RouteAnalyzer {

    override fun deriveFrom(traces: List<TraceItem>): String {
        for (t in traces.reversed()) {
            val p = extractProvince(t.desc)
            if (p.isNotEmpty()) return p
        }
        return ""
    }

    override fun deriveTo(traces: List<TraceItem>): String {
        for (t in traces) {
            val p = extractProvince(t.desc)
            if (p.isNotEmpty()) return p
        }
        return ""
    }

    override fun statusTag(traces: List<TraceItem>): String {
        val lastDesc = traces.firstOrNull()?.desc ?: return ""

        // 扫描所有轨迹：签收 / 顺丰已派送成功 / 顺丰单字状态
        val statusDesc = traces.firstOrNull {
            it.desc.contains("签收") ||
            it.desc.contains("已派送成功") ||
            it.desc.startsWith("收 ")
        }
        if (statusDesc != null) {
            val d = statusDesc.desc
            if (d.contains("已派送成功") || d.startsWith("收 ")) return "已签收"
            return when {
                d.contains("本人") -> "本人签收"
                d.contains("代签") || d.contains("家人") || d.contains("朋友") -> "代签收"
                d.contains("凭取件码") || d.contains("代收点") || d.contains("驿站") -> "已签收（代收点）"
                else -> "已签收"
            }
        }
        return when {
            lastDesc.contains("已投递") -> "已投递"
            lastDesc.contains("已送达") -> "已送达"
            lastDesc.contains("代收") || lastDesc.contains("暂存至") || lastDesc.contains("驿站") -> "到达代收点"
            lastDesc.contains("正在为您派送") || lastDesc.contains("派送") -> "派送中"
            lastDesc.contains("已到达") || lastDesc.contains("到达") -> "已到达"
            else -> ""
        }
    }

    private fun extractProvince(text: String): String {
        Regex(PROVINCES_JOINED).find(text)?.let { return it.value + "省" }
        for ((city, province) in CITIES_SORTED) {
            if (text.contains(city)) return province
        }
        return ""
    }

    companion object {
        private val PROVINCES = listOf(
            "北京", "天津", "上海", "重庆",
            "河北", "山西", "辽宁", "吉林", "黑龙江",
            "江苏", "浙江", "安徽", "福建", "江西", "山东",
            "河南", "湖北", "湖南", "广东", "海南",
            "四川", "贵州", "云南", "陕西", "甘肃", "青海",
            "内蒙古", "广西", "西藏", "宁夏", "新疆",
            "香港", "澳门",
        )
        private val PROVINCES_JOINED = PROVINCES.joinToString("|")

        private val CITY_PROVINCE_MAP = mapOf(
            "北京" to "北京", "天津" to "天津", "上海" to "上海", "重庆" to "重庆",

            "广州" to "广东省", "深圳" to "广东省", "珠海" to "广东省", "汕头" to "广东省",
            "佛山" to "广东省", "韶关" to "广东省", "湛江" to "广东省", "肇庆" to "广东省",
            "江门" to "广东省", "茂名" to "广东省", "惠州" to "广东省", "梅州" to "广东省",
            "汕尾" to "广东省", "河源" to "广东省", "阳江" to "广东省", "清远" to "广东省",
            "东莞" to "广东省", "中山" to "广东省", "潮州" to "广东省", "揭阳" to "广东省",
            "云浮" to "广东省",

            "南京" to "江苏省", "无锡" to "江苏省", "徐州" to "江苏省", "常州" to "江苏省",
            "苏州" to "江苏省", "南通" to "江苏省", "连云港" to "江苏省", "淮安" to "江苏省",
            "盐城" to "江苏省", "扬州" to "江苏省", "镇江" to "江苏省", "泰州" to "江苏省",
            "宿迁" to "江苏省", "昆山" to "江苏省", "常熟" to "江苏省", "张家港" to "江苏省",

            "杭州" to "浙江省", "宁波" to "浙江省", "温州" to "浙江省", "嘉兴" to "浙江省",
            "湖州" to "浙江省", "绍兴" to "浙江省", "金华" to "浙江省", "衢州" to "浙江省",
            "舟山" to "浙江省", "台州" to "浙江省", "丽水" to "浙江省", "义乌" to "浙江省",
            "慈溪" to "浙江省",

            "福州" to "福建省", "厦门" to "福建省", "莆田" to "福建省", "三明" to "福建省",
            "泉州" to "福建省", "漳州" to "福建省", "南平" to "福建省", "龙岩" to "福建省",
            "宁德" to "福建省", "晋江" to "福建省", "石狮" to "福建省",

            "成都" to "四川省", "绵阳" to "四川省", "德阳" to "四川省", "宜宾" to "四川省",
            "南充" to "四川省", "泸州" to "四川省", "自贡" to "四川省", "乐山" to "四川省",
            "资阳" to "四川省",

            "武汉" to "湖北省", "黄石" to "湖北省", "襄阳" to "湖北省", "荆州" to "湖北省",
            "宜昌" to "湖北省", "十堰" to "湖北省", "孝感" to "湖北省", "黄冈" to "湖北省",

            "长沙" to "湖南省", "株洲" to "湖南省", "湘潭" to "湖南省", "衡阳" to "湖南省",
            "岳阳" to "湖南省", "常德" to "湖南省",

            "郑州" to "河南省", "洛阳" to "河南省", "新乡" to "河南省", "安阳" to "河南省",
            "南阳" to "河南省", "开封" to "河南省", "焦作" to "河南省", "许昌" to "河南省",
            "驻马店" to "河南省", "漯河" to "河南省", "信阳" to "河南省", "周口" to "河南省",
            "平顶山" to "河南省", "商丘" to "河南省",

            "济南" to "山东省", "青岛" to "山东省", "淄博" to "山东省", "烟台" to "山东省",
            "潍坊" to "山东省", "济宁" to "山东省", "泰安" to "山东省", "临沂" to "山东省",
            "菏泽" to "山东省", "德州" to "山东省", "聊城" to "山东省", "枣庄" to "山东省",

            "石家庄" to "河北省", "唐山" to "河北省", "保定" to "河北省", "邯郸" to "河北省",
            "张家口" to "河北省", "廊坊" to "河北省", "沧州" to "河北省", "秦皇岛" to "河北省",

            "西安" to "陕西省", "宝鸡" to "陕西省", "咸阳" to "陕西省", "渭南" to "陕西省",
            "延安" to "陕西省", "汉中" to "陕西省",

            "沈阳" to "辽宁省", "大连" to "辽宁省", "鞍山" to "辽宁省", "抚顺" to "辽宁省",
            "锦州" to "辽宁省", "葫芦岛" to "辽宁省",

            "长春" to "吉林省", "吉林" to "吉林省", "四平" to "吉林省", "延边" to "吉林省",

            "哈尔滨" to "黑龙江省", "齐齐哈尔" to "黑龙江省", "大庆" to "黑龙江省",

            "合肥" to "安徽省", "芜湖" to "安徽省", "蚌埠" to "安徽省", "马鞍山" to "安徽省",
            "安庆" to "安徽省", "阜阳" to "安徽省",

            "南昌" to "江西省", "九江" to "江西省", "赣州" to "江西省", "上饶" to "江西省",
            "宜春" to "江西省",

            "太原" to "山西省", "大同" to "山西省", "临汾" to "山西省",

            "南宁" to "广西省", "柳州" to "广西省", "桂林" to "广西省",

            "贵阳" to "贵州省", "遵义" to "贵州省",

            "昆明" to "云南省", "大理" to "云南省",

            "兰州" to "甘肃省", "天水" to "甘肃省",

            "海口" to "海南省", "三亚" to "海南省",

            "呼和浩特" to "内蒙古", "包头" to "内蒙古",

            "乌鲁木齐" to "新疆", "银川" to "宁夏", "西宁" to "青海省", "拉萨" to "西藏",
            "香港" to "香港", "澳门" to "澳门",
        )

        private val CITIES_SORTED = CITY_PROVINCE_MAP.entries
            .sortedByDescending { it.key.length }
            .map { it.key to it.value }
    }
}
