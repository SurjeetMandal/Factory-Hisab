package com.example.voice

import com.example.data.local.PartyBalanceSummary
import com.example.data.local.ProductWithDetails
import kotlin.math.max
import kotlin.math.min

sealed interface MenuParseResult {
    data class Selected(val task: VoiceTaskType) : MenuParseResult
    data object OutOfScope : MenuParseResult
    data object Unclear : MenuParseResult
}

enum class ConfirmIntent {
    YES,
    NO,
    UNCLEAR
}

data class PaymentAmountAndMode(
    val amount: Double?,
    val mode: String?
)

object HindiHinglishNlu {

    private val devanagariDigits = mapOf(
        '०' to '0', '१' to '1', '२' to '2', '३' to '3', '४' to '4',
        '५' to '5', '६' to '6', '७' to '7', '८' to '8', '९' to '9'
    )

    private val independentVowels = mapOf(
        'अ' to "a", 'आ' to "aa", 'इ' to "i", 'ई' to "ee",
        'उ' to "u", 'ऊ' to "oo", 'ऋ' to "ri", 'ए' to "e",
        'ऐ' to "ai", 'ओ' to "o", 'औ' to "au", 'ऑ' to "o", 'ऍ' to "e"
    )

    private val vowelMatras = mapOf(
        'ा' to "aa", 'ि' to "i", 'ी' to "ee", 'ु' to "u", 'ू' to "oo",
        'ृ' to "ri", 'े' to "e", 'ै' to "ai", 'ो' to "o", 'ौ' to "au",
        'ॉ' to "o", 'ॅ' to "e"
    )

    private val consonants = mapOf(
        'क' to "k", 'ख' to "kh", 'ग' to "g", 'घ' to "gh", 'ङ' to "n",
        'च' to "ch", 'छ' to "chh", 'ज' to "j", 'झ' to "jh", 'ञ' to "n",
        'ट' to "t", 'ठ' to "th", 'ड' to "d", 'ढ' to "dh", 'ण' to "n",
        'त' to "t", 'थ' to "th", 'द' to "d", 'ध' to "dh", 'न' to "n",
        'प' to "p", 'फ' to "ph", 'ब' to "b", 'भ' to "bh", 'म' to "m",
        'य' to "y", 'र' to "r", 'ल' to "l", 'व' to "v",
        'श' to "sh", 'ष' to "sh", 'स' to "s", 'ह' to "h",
        'ळ' to "l"
    )

    private val multiCharClusters = listOf(
        "क्ष" to "ksh",
        "ज्ञ" to "gy",
        "त्र" to "tr",
        "श्र" to "shr",
        "फ\u093C" to "f",
        "ज\u093C" to "z",
        "क\u093C" to "q",
        "ख\u093C" to "kh",
        "ग\u093C" to "g",
        "ड\u093C" to "d",
        "ढ\u093C" to "dh"
    )

    fun transliterateDevanagariToLatin(input: String): String {
        val sb = StringBuilder(input.length * 2)
        var i = 0
        while (i < input.length) {
            val ch = input[i]

            val digit = devanagariDigits[ch]
            if (digit != null) {
                sb.append(digit)
                i++
                continue
            }

            val cluster = multiCharClusters.firstOrNull { (seq, _) ->
                input.startsWith(seq, startIndex = i)
            }
            if (cluster != null) {
                sb.append(cluster.second)
                i += cluster.first.length
                val post = input.getOrNull(i)
                when {
                    post == '्' -> i++
                    post != null && vowelMatras.containsKey(post) -> {
                        sb.append(vowelMatras[post])
                        i++
                    }
                    post == 'ं' || post == 'ँ' -> {
                        sb.append("an")
                        i++
                    }
                    post != null && (consonants.containsKey(post) || independentVowels.containsKey(post)) -> {
                        sb.append('a')
                    }
                }
                continue
            }

            val indVowel = independentVowels[ch]
            if (indVowel != null) {
                sb.append(indVowel)
                i++
                continue
            }

            val cons = consonants[ch]
            if (cons != null) {
                sb.append(cons)
                i++
                val post = input.getOrNull(i)
                when {
                    post == '्' -> i++
                    post != null && vowelMatras.containsKey(post) -> {
                        sb.append(vowelMatras[post])
                        i++
                    }
                    post == 'ं' || post == 'ँ' -> {
                        sb.append("an")
                        i++
                    }
                    post != null && (consonants.containsKey(post) || independentVowels.containsKey(post)) -> {
                        sb.append('a')
                    }
                }
                continue
            }

            when (ch) {
                'ं', 'ँ' -> sb.append('n')
                'ः' -> sb.append('h')
                '।' -> sb.append(' ')
                '्' -> {}
                else -> sb.append(ch)
            }
            i++
        }
        return sb.toString()
    }

    fun toNormalizedLatin(raw: String): String {
        val latin = transliterateDevanagariToLatin(raw)
        return latin.lowercase()
            .replace("₹", " ")
            .replace(Regex("(?<!\\d)\\.(?!\\d)"), " ")
            .replace(Regex("[^a-z0-9.\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun parseMenuChoice(raw: String): MenuParseResult {
        val norm = toNormalizedLatin(raw)
        if (norm.isBlank()) return MenuParseResult.Unclear

        val tokens = norm.split(" ").filter { it.isNotBlank() }

        // Check numeric choices first if short
        if (tokens.size <= 3) {
            when {
                tokens.any { it in setOf("1", "one", "ek", "first", "pehla", "pahla") } &&
                    !containsWord(norm, "thousand", "hazaar", "sau", "rupees") ->
                    return MenuParseResult.Selected(VoiceTaskType.ADD_PAYMENT)
                tokens.any { it in setOf("2", "two", "do", "second", "dusra", "doosra") } &&
                    !containsWord(norm, "thousand", "hazaar", "sau", "rupees") ->
                    return MenuParseResult.Selected(VoiceTaskType.ADD_HISAB)
                tokens.any { it in setOf("3", "three", "teen", "third", "teesra", "tisra") } ->
                    return MenuParseResult.Selected(VoiceTaskType.ADD_PARTY)
                tokens.any { it in setOf("4", "four", "char", "chaar", "fourth", "chautha") } ->
                    return MenuParseResult.Selected(VoiceTaskType.ADD_ITEM)
            }
        }

        val hasPayment = containsAny(norm, listOf("payment", "pement", "paisa", "paise", "vasool", "wasool", "jama", "paid", "pay", "udhaar chuka", "collection", "receive", "bhugtan"))
        val hasHisab = containsAny(norm, listOf("hisab", "hisaab", "khata", "bill", "parchi", "slip", "register", "entry", "kaam likh", "calculation"))
        val hasParty = containsAny(norm, listOf("party", "paarti", "client", "customer", "grahak", "vyapari", "firm", "dukan", "dookan", "company"))
        val hasItem = containsAny(norm, listOf("item", "aitam", "product", "maal", "saman", "saaman", "model", "piece rate", "rate set"))

        if (hasPayment && !hasHisab) return MenuParseResult.Selected(VoiceTaskType.ADD_PAYMENT)
        if (hasHisab) return MenuParseResult.Selected(VoiceTaskType.ADD_HISAB)
        if (hasItem && !hasParty) return MenuParseResult.Selected(VoiceTaskType.ADD_ITEM)
        if (hasItem && hasParty) {
            // "add new item under party" -> item
            return MenuParseResult.Selected(VoiceTaskType.ADD_ITEM)
        }
        if (hasParty) return MenuParseResult.Selected(VoiceTaskType.ADD_PARTY)

        val outOfScopeHints = listOf(
            "weather", "mausam", "news", "khabar", "joke", "chutkula", "song", "gaana",
            "music", "cricket", "match", "recipe", "khana", "email", "google", "youtube",
            "whatsapp", "call", "alarm", "time", "samay", "date", "tarikh", "prime minister",
            "story", "kahani", "poem", "kavita", "movie", "film", "capital", "translate"
        )
        if (containsAny(norm, outOfScopeHints) || tokens.size >= 4) {
            return MenuParseResult.OutOfScope
        }

        return MenuParseResult.Unclear
    }

    fun parseConfirmation(raw: String): ConfirmIntent {
        val norm = toNormalizedLatin(raw)
        if (norm.isBlank()) return ConfirmIntent.UNCLEAR

        val negativeWords = setOf(
            "no", "nope", "nah", "nahi", "nahin", "na", "naa", "mat", "cancel", "cancil",
            "stop", "ruko", "galat", "wrong", "band", "rehne", "chhodo", "hatao", "abort"
        )
        val positiveWords = setOf(
            "yes", "yeah", "yep", "yup", "ok", "okay", "sure", "confirm", "save", "done",
            "haan", "han", "ha", "haanji", "hanji", "ji", "theek", "thik", "sahi", "bilkul",
            "karo", "kardo", "daal", "daalo", "jodo", "jod", "banao", "banado", "add", "zaroor"
        )

        val tokens = norm.split(" ").filter { it.isNotBlank() }
        if (tokens.any { it in negativeWords } ||
            containsAny(norm, listOf("mat karo", "nahi karna", "nahi add", "band karo", "rehne do"))
        ) {
            return ConfirmIntent.NO
        }
        if (tokens.any { it in positiveWords } ||
            containsAny(norm, listOf("kar do", "add kar", "jod do", "save kar", "theek hai", "sahi hai", "ji haan"))
        ) {
            return ConfirmIntent.YES
        }
        return ConfirmIntent.UNCLEAR
    }

    fun isSkipCommand(raw: String): Boolean {
        val norm = toNormalizedLatin(raw)
        return containsAny(
            norm,
            listOf("skip", "chhodo", "chodo", "none", "nothing", "kuch nahi", "zero", "0", "nahi hai", "baad mein")
        )
    }

    fun isShowListCommand(raw: String): Boolean {
        val norm = toNormalizedLatin(raw)
        return containsAny(norm, listOf("show list", "list dikhao", "dikhao", "show parties", "array", "party list"))
    }

    fun parsePaymentMode(raw: String): String? {
        val norm = toNormalizedLatin(raw)
        val phonetic = phoneticCanonical(norm).replace(" ", "")
        if (norm.isBlank()) return null
        return when {
            containsAny(
                norm,
                listOf("upi", "yupi", "yupee", "gpay", "google pay", "phonepe", "phone pe", "fonpe", "paytm", "petiam", "bhim", "qr", "scanner", "online")
            ) || containsAny(
                phonetic,
                listOf("yupiai", "yupiaai", "yupi", "jipe", "fonpe", "petiam")
            ) -> "UPI"
            containsAny(
                norm,
                listOf("cash", "kaish", "kesh", "naqad", "nakad", "naqdi", "nakdi", "rokad", "rokar", "hath", "haath")
            ) -> "Cash"
            containsAny(
                norm,
                listOf("bank", "baink", "neft", "rtgs", "imps", "account", "khata", "transfer", "transfar")
            ) -> "Bank"
            containsAny(
                norm,
                listOf("cheque", "check", "chek", "dd", "draft")
            ) -> "Cheque"
            else -> null
        }
    }

    fun parsePaymentAmountAndMode(raw: String): PaymentAmountAndMode {
        val mode = parsePaymentMode(raw)
        val amount = parseNumber(raw)
        return PaymentAmountAndMode(amount = amount, mode = mode)
    }

    fun parseNumber(raw: String): Double? {
        val latin = transliterateDevanagariToLatin(raw)
            .lowercase()
            .replace("₹", " ")
            .replace(",", "")
            .trim()

        // 1. Check explicit numeric value first (supporting optional k/lakh/hazaar suffix)
        val regex = Regex("""(\d+(?:\.\d+)?)\s*(k|hazaar|hajar|thousand|lakh|lac|l|crore|cr)?\b""")
        val numMatch = regex.find(latin)
        if (numMatch != null) {
            val base = numMatch.groupValues[1].toDoubleOrNull()
            if (base != null && base >= 0.0) {
                val mult = when (numMatch.groupValues[2]) {
                    "k", "hazaar", "hajar", "thousand" -> 1_000.0
                    "lakh", "lac", "l" -> 100_000.0
                    "crore", "cr" -> 10_000_000.0
                    else -> 1.0
                }
                return base * mult
            }
        }

        // 2. Hindi / Hinglish / English spoken number words
        val cleaned = latin.replace(Regex("[^a-z\\s]"), " ").replace(Regex("\\s+"), " ").trim()
        if (cleaned.isBlank()) return null

        if (cleaned in setOf("zero", "shunya", "sifar", "nil", "kuch nahi")) return 0.0

        val tokens = cleaned.split(" ")
        var total = 0.0
        var current = 0.0
        var matchedAny = false

        var idx = 0
        while (idx < tokens.size) {
            val tok = tokens[idx]
            when (tok) {
                "sadhe", "saadhe", "sade" -> {
                    val nextVal = wordValue(tokens.getOrNull(idx + 1) ?: "")
                    if (nextVal != null) {
                        current += nextVal + 0.5
                        matchedAny = true
                        idx += 2
                        continue
                    }
                }
                "dedh", "derh" -> {
                    current += 1.5
                    matchedAny = true
                }
                "dhai", "dhaai", "aadh", "adhai" -> {
                    current += 2.5
                    matchedAny = true
                }
                "sava", "sawa" -> {
                    val nextVal = wordValue(tokens.getOrNull(idx + 1) ?: "")
                    if (nextVal != null) {
                        current += nextVal + 0.25
                        matchedAny = true
                        idx += 2
                        continue
                    }
                }
                "paune" -> {
                    val nextVal = wordValue(tokens.getOrNull(idx + 1) ?: "")
                    if (nextVal != null) {
                        current += (nextVal - 0.25).coerceAtLeast(0.0)
                        matchedAny = true
                        idx += 2
                        continue
                    }
                }
                "sau", "so", "sao", "hundred" -> {
                    current = (if (current == 0.0) 1.0 else current) * 100.0
                    matchedAny = true
                }
                "hazaar", "hajaar", "hajar", "hazar", "thousand" -> {
                    val base = if (current == 0.0) 1.0 else current
                    total += base * 1_000.0
                    current = 0.0
                    matchedAny = true
                }
                "lakh", "lac", "laakh" -> {
                    val base = if (current == 0.0) 1.0 else current
                    total += base * 100_000.0
                    current = 0.0
                    matchedAny = true
                }
                "crore", "karod" -> {
                    val base = if (current == 0.0) 1.0 else current
                    total += base * 10_000_000.0
                    current = 0.0
                    matchedAny = true
                }
                else -> {
                    val wv = wordValue(tok)
                    if (wv != null) {
                        current += wv
                        matchedAny = true
                    }
                }
            }
            idx++
        }

        val combined = total + current
        return if (matchedAny && combined >= 0.0) combined else null
    }

    fun cleanEntityNameInput(raw: String): String {
        val trimmed = raw.trim()
        val prefixes = listOf(
            "party ka naam hai", "party ka naam", "party name is", "party name",
            "item ka naam hai", "item ka naam", "item name is", "item name",
            "product ka naam", "product name is", "product name",
            "add party", "new party", "add item", "new item",
            "naam hai", "name is", "iska naam", "uska naam"
        )
        var result = trimmed
        for (prefix in prefixes) {
            if (result.lowercase().startsWith(prefix)) {
                result = result.drop(prefix.length).trim()
                break
            }
        }
        return result.trim(':', '-', '.', ',', ' ')
            .split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                if (word.any { it in '\u0900'..'\u097F' }) {
                    word
                } else {
                    word.replaceFirstChar { it.uppercaseChar() }
                }
            }
    }

    fun findBestPartyMatch(
        spokenRaw: String,
        parties: List<PartyBalanceSummary>
    ): PartyBalanceSummary? {
        if (parties.isEmpty()) return null
        val query = stripBusinessSuffixes(phoneticCanonical(toNormalizedLatin(spokenRaw)))
        if (query.length < 2) return null

        var best: PartyBalanceSummary? = null
        var bestScore = 0.0

        for (summary in parties) {
            val candidate = stripBusinessSuffixes(phoneticCanonical(toNormalizedLatin(summary.party.name)))
            if (candidate.isBlank()) continue
            val score = matchScore(query, candidate)
            if (score > bestScore) {
                bestScore = score
                best = summary
            }
        }

        return if (bestScore >= 0.62) best else null
    }

    fun findBestProductMatch(
        spokenRaw: String,
        products: List<ProductWithDetails>
    ): ProductWithDetails? {
        if (products.isEmpty()) return null
        val query = phoneticCanonical(toNormalizedLatin(spokenRaw))
        if (query.length < 2) return null

        var best: ProductWithDetails? = null
        var bestScore = 0.0

        for (product in products) {
            val candidate = phoneticCanonical(toNormalizedLatin(product.product.name))
            if (candidate.isBlank()) continue
            val score = matchScore(query, candidate)
            if (score > bestScore) {
                bestScore = score
                best = product
            }
        }

        return if (bestScore >= 0.62) best else null
    }

    private fun stripBusinessSuffixes(text: String): String {
        val stopWords = setOf(
            "pvt", "private", "praivet", "pravet", "praivat", "ltd", "limited", "limted",
            "co", "company", "kampani", "inc", "enterprises", "enterprise",
            "industries", "industry", "traders", "trader", "works", "varks",
            "engineering", "engg", "party", "parti", "naam", "ka", "ki", "ke",
            "wala", "wale", "sir", "ji", "bhai", "mr", "ms", "shree", "shri",
            "for", "to", "from"
        )
        val filtered = text.split(" ")
            .map { it.trim('.') }
            .filter { it.isNotBlank() && it !in stopWords }
        return if (filtered.isNotEmpty()) filtered.joinToString(" ") else text
    }

    private fun phoneticCanonical(latin: String): String {
        return latin
            .replace("ph", "f")
            .replace("sh", "s")
            .replace("bh", "b")
            .replace("kh", "k")
            .replace("gh", "g")
            .replace("chh", "ch")
            .replace("th", "t")
            .replace("dh", "d")
            .replace("oo", "u")
            .replace("ee", "i")
            .replace("aa", "a")
            .replace("ey", "i")
            .replace("ie", "i")
            .replace("ck", "k")
            .replace("w", "v")
            .replace("z", "j")
            .replace("q", "k")
            .replace(Regex("y\\b"), "i")
            .replace(Regex("([a-z])\\1+"), "$1")
            .trim()
    }

    private fun matchScore(query: String, candidate: String): Double {
        if (query == candidate) return 1.0
        val queryCompact = query.replace(" ", "")
        val candCompact = candidate.replace(" ", "")
        if (queryCompact == candCompact) return 0.98

        // Substring check when query is meaningful
        if (queryCompact.length >= 3 && (candCompact.contains(queryCompact) || queryCompact.contains(candCompact))) {
            val ratio = min(queryCompact.length, candCompact.length).toDouble() /
                max(queryCompact.length, candCompact.length).toDouble()
            return 0.72 + (ratio * 0.25)
        }

        val qTokens = query.split(" ").filter { it.length >= 2 }.toSet()
        val cTokens = candidate.split(" ").filter { it.length >= 2 }.toSet()

        if (qTokens.isNotEmpty() && cTokens.isNotEmpty()) {
            var tokenMatches = 0.0
            for (qt in qTokens) {
                var bestTok = 0.0
                for (ct in cTokens) {
                    val sim = if (qt == ct) 1.0 else {
                        val d = weightedPhoneticDistance(qt, ct)
                        1.0 - (d / max(qt.length, ct.length).toDouble())
                    }
                    if (sim > bestTok) bestTok = sim
                }
                if (bestTok >= 0.68) tokenMatches += bestTok
            }
            val queryCoverage = tokenMatches / qTokens.size.toDouble()
            val candidateCoverage = tokenMatches / cTokens.size.toDouble()
            val tokenOverlapScore = (queryCoverage * 0.65) + (candidateCoverage * 0.35)
            val firstTokenBoost = if (cTokens.any { ct ->
                    val qt = qTokens.first()
                    ct.startsWith(qt) || qt.startsWith(ct) || weightedPhoneticDistance(qt, ct) <= 1.0
                }
            ) 0.10 else 0.0
            val combinedToken = (tokenOverlapScore + firstTokenBoost).coerceAtMost(0.95)
            if (combinedToken >= 0.62) return combinedToken
        }

        val dist = weightedPhoneticDistance(queryCompact, candCompact)
        return (1.0 - dist / max(queryCompact.length, candCompact.length).toDouble())
            .coerceAtLeast(0.0)
    }

    private fun weightedPhoneticDistance(a: String, b: String): Double {
        if (a == b) return 0.0
        if (a.isEmpty()) return b.length.toDouble()
        if (b.isEmpty()) return a.length.toDouble()
        val vowels = setOf('a', 'e', 'i', 'o', 'u')
        val prev = DoubleArray(b.length + 1) { it.toDouble() }
        val curr = DoubleArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i.toDouble()
            for (j in 1..b.length) {
                val ca = a[i - 1]
                val cb = b[j - 1]
                val subCost = when {
                    ca == cb -> 0.0
                    ca in vowels && cb in vowels -> 0.35
                    else -> 1.0
                }
                curr[j] = minOf(
                    curr[j - 1] + 1.0,
                    prev[j] + 1.0,
                    prev[j - 1] + subCost
                )
            }
            for (j in 0..b.length) prev[j] = curr[j]
        }
        return prev[b.length]
    }

    private fun containsAny(haystack: String, needles: List<String>): Boolean =
        needles.any { haystack.contains(it) }

    private fun containsWord(haystack: String, vararg words: String): Boolean {
        val tokens = haystack.split(" ").toSet()
        return words.any { it in tokens }
    }

    private fun wordValue(word: String): Double? = when (word) {
        "ek", "aik", "one" -> 1.0
        "do", "two" -> 2.0
        "teen", "tin", "three" -> 3.0
        "char", "chaar", "four" -> 4.0
        "paanch", "panch", "five" -> 5.0
        "chhah", "chhe", "che", "six" -> 6.0
        "saat", "sat", "seven" -> 7.0
        "aath", "ath", "eight" -> 8.0
        "nau", "no", "nine" -> 9.0
        "das", "dus", "ten" -> 10.0
        "gyarah", "gyaarah", "eleven" -> 11.0
        "barah", "baarah", "twelve" -> 12.0
        "terah", "thirteen" -> 13.0
        "chaudah", "chodah", "fourteen" -> 14.0
        "pandrah", "fifteen" -> 15.0
        "solah", "sixteen" -> 16.0
        "satrah", "seventeen" -> 17.0
        "atharah", "attharah", "eighteen" -> 18.0
        "unnis", "unees", "nineteen" -> 19.0
        "bees", "bis", "twenty" -> 20.0
        "pachis", "pachchis", "pachees", "twentyfive" -> 25.0
        "tees", "tis", "thirty" -> 30.0
        "chalis", "chaalis", "forty" -> 40.0
        "pachas", "pachaas", "fifty" -> 50.0
        "saath", "sath", "sixty" -> 60.0
        "sattar", "seventy" -> 70.0
        "assi", "assee", "eighty" -> 80.0
        "nabbe", "nambhe", "ninety" -> 90.0
        else -> null
    }
}
