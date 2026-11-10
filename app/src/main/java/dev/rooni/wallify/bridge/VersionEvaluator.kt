package dev.rooni.wallify.bridge

object VersionEvaluator {

    data class SemVer(val major: Int, val minor: Int, val patch: Int) : Comparable<SemVer> {
        override fun compareTo(other: SemVer): Int {
            if (this.major != other.major) return this.major.compareTo(other.major)
            if (this.minor != other.minor) return this.minor.compareTo(other.minor)
            return this.patch.compareTo(other.patch)
        }
    }

    fun parseSemVer(versionStr: String): SemVer {
        val clean = versionStr.trim().replace(Regex("[^0-9.]"), "")
        val parts = clean.split(".")
        val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
        return SemVer(major, minor, patch)
    }

    fun isNewer(candidate: String, installed: String): Boolean =
        parseSemVer(candidate) > parseSemVer(installed)

    fun isCompatible(appVersionStr: String, reqVersionStr: String?): Pair<Boolean, String> {
        if (reqVersionStr == null || reqVersionStr.trim().isEmpty()) {
            return Pair(true, "")
        }

        val trimmedReq = reqVersionStr.trim()
        val currentAppVersion = parseSemVer(appVersionStr)

        return try {
            val (operator, reqVerString) = when {
                trimmedReq.startsWith(">=") -> Pair(">=", trimmedReq.substring(2))
                trimmedReq.startsWith("<=") -> Pair("<=", trimmedReq.substring(2))
                trimmedReq.startsWith(">") -> Pair(">", trimmedReq.substring(1))
                trimmedReq.startsWith("<") -> Pair("<", trimmedReq.substring(1))
                trimmedReq.startsWith("==") -> Pair("==", trimmedReq.substring(2))
                else -> Pair(">=", trimmedReq)
            }

            val requiredVersion = parseSemVer(reqVerString)
            val cmp = currentAppVersion.compareTo(requiredVersion)

            val isSatisfied = when (operator) {
                ">=" -> cmp >= 0
                "<=" -> cmp <= 0
                ">" -> cmp > 0
                "<" -> cmp < 0
                "==" -> cmp == 0
                else -> cmp >= 0
            }

            if (isSatisfied) Pair(true, "") else Pair(false, trimmedReq)
        } catch (e: Exception) {
            Pair(true, "")
        }
    }
}
