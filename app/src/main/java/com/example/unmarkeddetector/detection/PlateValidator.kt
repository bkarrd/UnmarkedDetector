package com.example.unmarkeddetector.detection

object PlateValidator {

    private val plateRegex = Regex(
        """(?<![A-Z0-9])([A-Z]{2,3}[\s\-]?[A-Z0-9]{4,5})(?![A-Z0-9])""",
        RegexOption.IGNORE_CASE
    )

    // Includes Polish prefixes and local seed prefixes used in tests and bundled data.
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
            .uppercase()
            .replace(Regex("[^A-Z0-9\\s-]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun extractPlates(rawText: String): List<String> {
        val cleaned = cleanOCRResult(rawText)
        val directMatches = plateRegex.findAll(cleaned)
            .map { match -> match.groupValues[1] }
            .map { candidate -> candidate.replace(Regex("[\\s-]"), "").uppercase() }
            .mapNotNull(::canonicalizePolishPlate)
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
        return canonicalizePolishPlate(text) != null
    }

    private fun extractCompactCandidates(cleanedText: String): List<String> {
        val compact = cleanedText.replace(Regex("[\\s-]"), "")
        if (compact.length < 6) return emptyList()

        val matches = linkedSetOf<String>()
        for (length in 6..8) {
            for (start in 0..compact.length - length) {
                val candidate = compact.substring(start, start + length)
                canonicalizePolishPlate(candidate)?.let(matches::add)
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

    private fun canonicalizePolishPlate(text: String): String? {
        val clean = text.replace(Regex("[\\s-]"), "").uppercase()
        if (clean.length !in 6..8 || !clean.all(Char::isLetterOrDigit)) return null

        listOf(3, 2).forEach { prefixLength ->
            val prefix = clean.take(prefixLength)
            val suffix = clean.drop(prefixLength)
            if (prefix in validPrefixes && isValidSuffix(suffix)) return prefix + suffix
        }

        listOf(2, 3).forEach { prefixLength ->
            val prefix = clean.take(prefixLength).map(::normalizePrefixCharacter).joinToString("")
            val suffix = clean.drop(prefixLength)
            if (prefix in validPrefixes && isValidSuffix(suffix)) return prefix + suffix
        }

        listOf(3, 2).forEach { prefixLength ->
            val prefix = repairPrefix(clean.take(prefixLength)) ?: return@forEach
            val suffix = clean.drop(prefixLength)
            if (isValidSuffix(suffix)) return prefix + suffix
        }
        return null
    }

    private fun isValidSuffix(suffix: String): Boolean {
        return suffix.length in 4..5 &&
            suffix.all(Char::isLetterOrDigit) &&
            suffix.any(Char::isDigit)
    }

    private fun normalizePrefixCharacter(character: Char): Char {
        return when (character) {
            '0' -> 'O'
            '1' -> 'I'
            '5' -> 'S'
            '8' -> 'B'
            else -> character
        }
    }

    private fun repairPrefix(prefix: String): String? {
        val candidates = validPrefixes
            .asSequence()
            .filter { candidate -> candidate.length == prefix.length }
            .filter { candidate ->
                val differences = prefix.indices.filter { index -> prefix[index] != candidate[index] }
                differences.size == 1 &&
                    isLikelyPrefixMistake(
                        raw = prefix[differences.single()],
                        expected = candidate[differences.single()],
                        index = differences.single()
                    )
            }
            .toList()
        return candidates.singleOrNull()
    }

    private fun isLikelyPrefixMistake(raw: Char, expected: Char, index: Int): Boolean {
        return normalizePrefixCharacter(raw) == expected ||
            (index == 0 && raw == 'O' && expected == 'W')
    }
}
