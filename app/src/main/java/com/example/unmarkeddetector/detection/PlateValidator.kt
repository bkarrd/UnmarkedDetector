package com.example.unmarkeddetector.detection

object PlateValidator {

    private val plateRegex = Regex(
        """(?<![A-Z0-9])([A-Z]{2,3}[\s\-]?[A-Z0-9]{4,5})(?![A-Z0-9])""",
        RegexOption.IGNORE_CASE
    )

    // Includes a wide set of Polish prefixes plus fictional MVP seed prefixes used in tests/data.
    private val validPrefixes = setOf(
        "BA", "BIA", "BBI", "BGR", "BHA", "BI", "BIA", "BKL", "BMN", "BS", "BSE", "BSI",
        "BSK", "BSU", "BWM", "BZA", "CB", "CG", "CGD", "CGR", "CIN", "CLI", "CM", "CNA",
        "CRA", "CSE", "CT", "CTR", "CW", "CWA", "CWL", "CZN", "DB", "DBA", "DBL", "DDZ",
        "DGL", "DGR", "DJ", "DJA", "DJE", "DKA", "DKL", "DL", "DLB", "DLU", "DMI", "DOL",
        "DPL", "DSR", "DST", "DSW", "DTR", "DW", "DWL", "DWR", "DX", "EL", "ELA", "ELB",
        "ELE", "EKU", "EPA", "EPA", "EPI", "EPJ", "EPO", "ERA", "ERW", "ESI", "ESK", "ETM",
        "EWE", "EZD", "FG", "FGW", "FKR", "FSD", "FSU", "FSW", "FWS", "FZG", "GA", "GBY",
        "GD", "GDA", "GKA", "GKS", "GKW", "GLE", "GMB", "GND", "GNI", "GNS", "GPU", "GS",
        "GSL", "GSP", "GST", "GSZ", "GTC", "GWE", "GWO", "HPA", "KA", "KAT", "KBC", "KBR",
        "KCH", "KDA", "KGR", "KLI", "KMI", "KMY", "KNS", "KNT", "KOL", "KOS", "KPR", "KRA",
        "KR", "KRM", "KSU", "KTA", "KTT", "KWA", "KWI", "KZA", "LC", "LBL", "LCU", "LKS",
        "LLB", "LLE", "LLU", "LKR", "LKS", "LOP", "LPA", "LPU", "LRA", "LSW", "LTM", "LU",
        "LUB", "LUB", "LWL", "LZA", "LZ", "NBA", "NBR", "NDZ", "NE", "NEL", "NGI", "NGO",
        "NIL", "NKE", "NLI", "NMR", "NNI", "NNO", "NO", "NOS", "NPI", "NSZ", "NWE", "NWM",
        "ONY", "OP", "OPR", "OS", "OST", "OT", "PCH", "PCT", "PGN", "PGO", "PGS", "PKA",
        "PKL", "PKN", "PKR", "PL", "PLE", "PLN", "PO", "POB", "POZ", "PPL", "PRA", "PSL",
        "PSR", "PSE", "PSZ", "PTU", "PWA", "PWL", "PWR", "PZ", "PZL", "PZN", "RBR", "RDE",
        "RJA", "RJS", "RKL", "RKR", "RLA", "RLE", "RLS", "RLU", "RMI", "RNI", "RP", "RPZ",
        "RPR", "RRS", "RSA", "RTA", "RZE", "SCI", "SD", "SE", "SG", "SGR", "SI", "SJZ",
        "SK", "SKL", "SL", "SM", "SO", "SRC", "SR", "STA", "ST", "STA", "STY", "SU", "SW",
        "SWD", "SZ", "SZO", "TA", "TBU", "TJE", "TK", "TKI", "TLW", "TOP", "TOR", "TSK",
        "TST", "TTR", "WA", "WAW", "WB", "WBR", "WD", "WE", "WF", "WG", "WH", "WI", "WJ",
        "WK", "WKA", "WL", "WLI", "WMA", "WND", "WOS", "WOT", "WPI", "WPL", "WPN", "WPR",
        "WPY", "WPU", "WPZ", "WS", "WSE", "WSI", "WSK", "WSZ", "WT", "WU", "WWL", "WWR",
        "WX", "WY", "WZ", "WZL", "WZU", "ZA", "ZBI", "ZCH", "ZDR", "ZGL", "ZG", "ZGR",
        "ZGY", "ZKA", "ZKL", "ZKO", "ZLO", "ZMY", "ZPL", "ZPY", "ZS", "ZSD", "ZST", "ZSW",
        "ZSZ", "ZT", "ZWA"
    )

    fun cleanOCRResult(raw: String): String {
        return raw
            .replace(Regex("[^A-Z0-9\\s-]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .uppercase()
    }

    fun extractPlates(rawText: String): List<String> {
        val cleaned = cleanOCRResult(rawText)
        val directMatches = plateRegex.findAll(cleaned)
            .map { match -> match.groupValues[1] }
            .map { candidate -> candidate.replace(Regex("[\\s-]"), "").uppercase() }
            .filter { isValidPolishPlate(it) }
            .toList()

        val compactMatches = extractCompactCandidates(cleaned)
        val strict = (directMatches + compactMatches).distinct()
        if (strict.isNotEmpty()) return strict

        return extractLenientPlates(cleaned)
    }

    /** Gdy OCR myli prefiks / spacje — dopasowanie formatu bez listy prefiksów. */
    private fun extractLenientPlates(cleaned: String): List<String> {
        val compact = cleaned.replace(Regex("[\\s-]"), "")
        if (compact.length < 6) return emptyList()
        val lenient = Regex("[A-Z]{2,3}[0-9][A-Z0-9]{3,5}", RegexOption.IGNORE_CASE)
        return lenient.findAll(compact)
            .map { it.value.uppercase() }
            .filter { it.length in 6..8 }
            .filter { it.matches(Regex("[A-Z]{2,3}[0-9][A-Z0-9]{3,5}")) }
            .distinct()
            .toList()
    }

    fun isValidPolishPlate(text: String): Boolean {
        val clean = text.replace(Regex("[\\s-]"), "").uppercase()
        if (clean.length !in 6..8) return false

        val prefix2 = clean.take(2)
        val prefix3 = clean.take(3)
        val hasValidPrefix = prefix2 in validPrefixes || prefix3 in validPrefixes
        val hasValidFormat = clean.matches(Regex("[A-Z]{2,3}[0-9][A-Z0-9]{3,4}"))
        return hasValidPrefix && hasValidFormat
    }

    private fun extractCompactCandidates(cleanedText: String): List<String> {
        val compact = cleanedText.replace(Regex("[\\s-]"), "")
        if (compact.length < 6) return emptyList()

        val matches = linkedSetOf<String>()
        for (length in 6..8) {
            for (start in 0..compact.length - length) {
                val candidate = compact.substring(start, start + length)
                if (isValidPolishPlate(candidate)) {
                    matches += candidate
                }
            }
        }
        val selected = mutableListOf<String>()
        matches
            .sortedWith(
                compareBy<String> { kotlin.math.abs(it.length - 7) }
                    .thenByDescending { it.length }
                    .thenBy { it }
            )
            .forEach { candidate ->
                if (selected.none { existing -> existing.contains(candidate) }) {
                    selected += candidate
                }
            }
        return selected
    }
}
