package io.github.davidx3d.tempest.data.device

enum class SocVendor(val displayName: String) {
    Qualcomm("Qualcomm"),
    MediaTek("MediaTek"),
    Samsung("Samsung"),
    Google("Google"),
    Unisoc("Unisoc"),
    HiSilicon("HiSilicon"),
    Unknown(""),
}

/** Marketing name and process node for a chip code such as "SM8650" or "MT6989". */
data class SocName(val name: String, val process: String? = null)

/**
 * Turns the cryptic codes Android reports (Build.SOC_MODEL, ro.board.platform, /proc/cpuinfo)
 * into names people know. Unknown chips keep their code, so nothing is ever hidden.
 */
object SocCatalog {

    fun vendor(vararg hints: String?): SocVendor {
        val text = hints.filterNotNull().joinToString(" ").lowercase()
        return when {
            Regex("qti|qualcomm|qcom|snapdragon|\\bsm\\d{4}|sdm\\d|msm\\d").containsMatchIn(text) -> SocVendor.Qualcomm
            Regex("mediatek|\\bmt\\d{4}|\\bmt6|dimensity|helio").containsMatchIn(text) -> SocVendor.MediaTek
            Regex("tensor|\\bgs\\d{3}|zuma|laguna").containsMatchIn(text) -> SocVendor.Google
            Regex("exynos|s5e\\d|samsung|universal\\d").containsMatchIn(text) -> SocVendor.Samsung
            Regex("unisoc|spreadtrum|sprd|\\bums\\d|\\bsc\\d{4}|\\bt\\d{3}\\b").containsMatchIn(text) -> SocVendor.Unisoc
            Regex("kirin|hisilicon|\\bhi\\d{4}").containsMatchIn(text) -> SocVendor.HiSilicon
            else -> SocVendor.Unknown
        }
    }

    /** Looks up every hint in turn, model codes first, then board platform code names. */
    fun name(vararg hints: String?): SocName? {
        hints.filterNotNull().map { it.trim().lowercase() }.filter { it.isNotEmpty() }.forEach { hint ->
            CODES.entries.firstOrNull { (code, _) -> hint == code || hint.startsWith("$code-") || hint.startsWith(code) }
                ?.let { return it.value }
            BOARDS[hint]?.let { return it }
        }
        return null
    }

    private val CODES: Map<String, SocName> = linkedMapOf(
        // Qualcomm Snapdragon
        "sm8850" to SocName("Snapdragon 8 Elite Gen 5", "3 nm"),
        "sm8750" to SocName("Snapdragon 8 Elite", "3 nm"),
        "sm8735" to SocName("Snapdragon 8s Gen 4", "4 nm"),
        "sm8650" to SocName("Snapdragon 8 Gen 3", "4 nm"),
        "sm8635" to SocName("Snapdragon 8s Gen 3", "4 nm"),
        "sm8550" to SocName("Snapdragon 8 Gen 2", "4 nm"),
        "sm8475" to SocName("Snapdragon 8+ Gen 1", "4 nm"),
        "sm8450" to SocName("Snapdragon 8 Gen 1", "4 nm"),
        "sm8350" to SocName("Snapdragon 888", "5 nm"),
        "sm8325" to SocName("Snapdragon 888+", "5 nm"),
        "sm8250" to SocName("Snapdragon 865 / 870", "7 nm"),
        "sm8150" to SocName("Snapdragon 855", "7 nm"),
        "sdm845" to SocName("Snapdragon 845", "10 nm"),
        "msm8998" to SocName("Snapdragon 835", "10 nm"),
        "sm7675" to SocName("Snapdragon 7+ Gen 3", "4 nm"),
        "sm7550" to SocName("Snapdragon 7 Gen 3", "4 nm"),
        "sm7475" to SocName("Snapdragon 7+ Gen 2", "4 nm"),
        "sm7450" to SocName("Snapdragon 7 Gen 1", "4 nm"),
        "sm7435" to SocName("Snapdragon 7s Gen 2", "4 nm"),
        "sm7325" to SocName("Snapdragon 778G", "6 nm"),
        "sm7250" to SocName("Snapdragon 765G", "7 nm"),
        "sm7225" to SocName("Snapdragon 750G", "8 nm"),
        "sm7150" to SocName("Snapdragon 730G", "8 nm"),
        "sm7125" to SocName("Snapdragon 720G", "8 nm"),
        "sm6450" to SocName("Snapdragon 6 Gen 1", "4 nm"),
        "sm6375" to SocName("Snapdragon 695", "6 nm"),
        "sm6350" to SocName("Snapdragon 690", "8 nm"),
        "sm6225" to SocName("Snapdragon 680", "6 nm"),
        "sm6125" to SocName("Snapdragon 665", "11 nm"),
        "sm6115" to SocName("Snapdragon 662", "11 nm"),
        "sm4450" to SocName("Snapdragon 4 Gen 2", "4 nm"),
        "sm4375" to SocName("Snapdragon 4 Gen 1", "6 nm"),
        "sm4350" to SocName("Snapdragon 480", "8 nm"),
        "sdm660" to SocName("Snapdragon 660", "14 nm"),
        "sdm450" to SocName("Snapdragon 450", "14 nm"),
        "sdm439" to SocName("Snapdragon 439", "12 nm"),
        // MediaTek Dimensity / Helio
        "mt6995" to SocName("Dimensity 9600 Pro", "2 nm"),
        "mt6993" to SocName("Dimensity 9500", "3 nm"),
        "mt6991" to SocName("Dimensity 9400", "3 nm"),
        "mt6989" to SocName("Dimensity 9300", "4 nm"),
        "mt6985" to SocName("Dimensity 9200", "4 nm"),
        "mt6983" to SocName("Dimensity 9000", "4 nm"),
        "mt6899" to SocName("Dimensity 8400", "4 nm"),
        "mt6897" to SocName("Dimensity 8300", "4 nm"),
        "mt6896" to SocName("Dimensity 8200", "4 nm"),
        "mt6895" to SocName("Dimensity 8100", "5 nm"),
        "mt6893" to SocName("Dimensity 1200", "6 nm"),
        "mt6891" to SocName("Dimensity 1100", "6 nm"),
        "mt6889" to SocName("Dimensity 1000+", "7 nm"),
        "mt6886" to SocName("Dimensity 7200", "4 nm"),
        "mt6878" to SocName("Dimensity 7300", "4 nm"),
        "mt6877" to SocName("Dimensity 900 / 1080", "6 nm"),
        "mt6853" to SocName("Dimensity 720 / 800U", "7 nm"),
        "mt6835" to SocName("Dimensity 6100+ / 6300", "6 nm"),
        "mt6833" to SocName("Dimensity 700 / 6020 / 6080", "7 nm"),
        "mt6789" to SocName("Helio G99", "6 nm"),
        "mt6785" to SocName("Helio G90T / G95", "12 nm"),
        "mt6781" to SocName("Helio G96", "12 nm"),
        "mt6779" to SocName("Helio P90", "12 nm"),
        "mt6771" to SocName("Helio P60 / P70", "12 nm"),
        "mt6769" to SocName("Helio G85 / G88", "12 nm"),
        "mt6768" to SocName("Helio G85 / P65", "12 nm"),
        "mt6765" to SocName("Helio P35 / G35 / G37", "12 nm"),
        "mt6762" to SocName("Helio P22 / G25", "12 nm"),
        // Samsung Exynos
        "s5e9955" to SocName("Exynos 2500", "3 nm"),
        "s5e9945" to SocName("Exynos 2400", "4 nm"),
        "s5e9925" to SocName("Exynos 2200", "4 nm"),
        "s5e9840" to SocName("Exynos 2100", "5 nm"),
        "s5e8855" to SocName("Exynos 1580", "4 nm"),
        "s5e8845" to SocName("Exynos 1480", "4 nm"),
        "s5e8835" to SocName("Exynos 1380", "5 nm"),
        "s5e8825" to SocName("Exynos 1280", "5 nm"),
        "exynos990" to SocName("Exynos 990", "7 nm"),
        "exynos9825" to SocName("Exynos 9825", "7 nm"),
        "exynos9820" to SocName("Exynos 9820", "8 nm"),
        // Google Tensor
        "gs101" to SocName("Google Tensor", "5 nm"),
        "gs201" to SocName("Google Tensor G2", "5 nm"),
        "zumapro" to SocName("Google Tensor G4", "4 nm"),
        "zuma" to SocName("Google Tensor G3", "4 nm"),
        "laguna" to SocName("Google Tensor G5", "3 nm"),
    )

    /** ro.board.platform code names, used when Build.SOC_MODEL is empty or generic. */
    private val BOARDS: Map<String, SocName> = mapOf(
        "canoe" to CODES.getValue("sm8850"),
        "sun" to CODES.getValue("sm8750"),
        "pineapple" to CODES.getValue("sm8650"),
        "kalama" to CODES.getValue("sm8550"),
        "taro" to CODES.getValue("sm8450"),
        "lahaina" to CODES.getValue("sm8350"),
        "kona" to CODES.getValue("sm8250"),
        "msmnile" to CODES.getValue("sm8150"),
        "crow" to CODES.getValue("sm7550"),
        "yupik" to CODES.getValue("sm7325"),
        "lito" to CODES.getValue("sm7250"),
        "atoll" to CODES.getValue("sm7125"),
        "holi" to CODES.getValue("sm6375"),
        "bengal" to CODES.getValue("sm6115"),
    )
}

/** ARM "CPU implementer" and "CPU part" from /proc/cpuinfo to a core name. */
object CoreNames {
    fun of(implementer: Int?, part: Int?): String? {
        if (part == null) return null
        return when (implementer) {
            0x41 -> ARM[part]
            0x51 -> QUALCOMM[part]
            0x53 -> SAMSUNG[part]
            else -> ARM[part]
        }
    }

    private val ARM = mapOf(
        0xd03 to "Cortex-A53", 0xd04 to "Cortex-A35", 0xd05 to "Cortex-A55", 0xd07 to "Cortex-A57",
        0xd08 to "Cortex-A72", 0xd09 to "Cortex-A73", 0xd0a to "Cortex-A75", 0xd0b to "Cortex-A76",
        0xd0d to "Cortex-A77", 0xd41 to "Cortex-A78", 0xd44 to "Cortex-X1", 0xd46 to "Cortex-A510",
        0xd47 to "Cortex-A710", 0xd48 to "Cortex-X2", 0xd4d to "Cortex-A715", 0xd4e to "Cortex-X3",
        0xd80 to "Cortex-A520", 0xd81 to "Cortex-A720", 0xd82 to "Cortex-X4", 0xd85 to "Cortex-X925",
        0xd87 to "Cortex-A725", 0xd8e to "Cortex-A720AE",
    )

    private val QUALCOMM = mapOf(
        0x001 to "Oryon", 0x002 to "Oryon",
        0x800 to "Kryo Gold", 0x801 to "Kryo Silver", 0x802 to "Kryo Gold", 0x803 to "Kryo Silver",
        0x804 to "Kryo Gold", 0x805 to "Kryo Silver",
    )

    private val SAMSUNG = mapOf(0x001 to "Mongoose M1", 0x002 to "Mongoose M3", 0x003 to "Mongoose M4", 0x004 to "Mongoose M5")
}
