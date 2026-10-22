package com.github.aashishvibhu.credentialmanagement.util

/** Rough 0..1 score from length and character variety; drives the strength meters. */
fun passwordStrength(p: String): Float {
    if (p.isEmpty()) return 0f
    var s = 0
    if (p.length >= 8) s++
    if (p.length >= 12) s++
    if (p.any { it.isUpperCase() }) s++
    if (p.any { it.isLowerCase() }) s++
    if (p.any { it.isDigit() }) s++
    if (p.any { !it.isLetterOrDigit() }) s++
    return s / 6f
}
